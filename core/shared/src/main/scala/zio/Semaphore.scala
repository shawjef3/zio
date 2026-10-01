/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.TSemaphore

import scala.annotation.tailrec

/**
 * An asynchronous semaphore, which is a generalization of a mutex. Semaphores
 * have a certain number of permits, which can be held and released concurrently
 * by different parties. Attempts to acquire more permits than available result
 * in the acquiring fiber being suspended until the specified number of permits
 * become available.
 *
 * If you need functionality that `Semaphore` doesnt' provide, use a
 * [[TSemaphore]] and define it in a [[zio.stm.ZSTM]] transaction.
 */
sealed trait Semaphore extends Serializable {

  /**
   * Returns the number of available permits.
   */
  def available(implicit trace: Trace): UIO[Long]

  /**
   * Returns the number of tasks currently waiting for permits. The default
   * implementation returns 0.
   */
  def awaiting(implicit trace: Trace): UIO[Long] = ZIO.succeed(0L)

  /**
   * Executes the effect, acquiring a permit if available and releasing it after
   * execution. Returns `None` if no permits were available.
   */
  final def tryWithPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    tryWithPermits(1L)(zio)

  /**
   * Executes the effect, acquiring `n` permits if available and releasing them
   * after execution. Returns `None` if no permits were available.
   */
  def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZIO.none

  /**
   * Executes the specified workflow, acquiring a permit immediately before the
   * workflow begins execution and releasing it immediately after the workflow
   * completes execution, whether by success, failure, or interruption.
   */
  def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  /**
   * Returns a scoped workflow that describes acquiring a permit as the
   * `acquire` action and releasing it as the `release` action.
   */
  def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit]

  /**
   * Executes the specified workflow, acquiring the specified number of permits
   * immediately before the workflow begins execution and releasing them
   * immediately after the workflow completes execution, whether by success,
   * failure, or interruption.
   */
  def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  /**
   * Returns a scoped workflow that describes acquiring the specified number of
   * permits and releasing them when the scope is closed.
   */
  def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit]

}

object Semaphore {

  /**
   * Creates a new `Semaphore` with the specified number of permits. Fibers
   * waiting for permits are served in FIFO order.
   */
  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.make(permits)(Unsafe))

  /**
   * Creates a new unfair `Semaphore` with the specified number of permits.
   *
   * Permits go to whichever fiber asks first, even if other fibers are already
   * waiting, so a fiber that releases and immediately re-acquires usually keeps
   * its permits without suspending. This gives higher throughput under
   * contention than [[make]] at the cost of ordering: a waiting fiber may be
   * overtaken indefinitely.
   */
  def makeUnfair(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.makeUnfair(permits)(Unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new Fair(permits)

    def makeUnfair(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new Unfair(permits)
  }

  private val resumeNow: Either[Nothing, Exit[Nothing, Unit]] = Right(Exit.unit)
  private val noFiber: () => FiberId                          = () => FiberId.None

  /**
   * A fiber waiting for `n` permits. All fields are guarded by the owning
   * semaphore's waiter-queue lock. `woken` is set when the semaphore hands the
   * waiter its resumption, either because it was granted its permits (fair) or
   * because it should retry for them (unfair); `holding` is set once the waiter
   * owns its permits.
   */
  private final class Waiter(val n: Long) {
    var callback: ZIO[Any, Nothing, Unit] => Unit = null
    var woken: Boolean                            = false
    var holding: Boolean                          = false
  }

  /**
   * The parts shared by both implementations: a counter of free permits with a
   * lock-free fast path, a lock-guarded FIFO queue of waiters, and the
   * `withPermits` family built on `reserve`, `await`, `release` and
   * `cancelOrRelease`.
   */
  private sealed abstract class Base(initial: Long) extends Semaphore {

    /**
     * Permits not held by anyone. Taken on the fast path with a single CAS and
     * returned with a single atomic add.
     */
    protected final val permits = new java.util.concurrent.atomic.AtomicLong(initial)

    /**
     * True whenever `waiters` may be non-empty. Written under the lock, read
     * without it. A waiter publishes `true` before re-reading `permits`, and a
     * releaser adds to `permits` before reading this flag, so one of the two
     * always observes the other and no wakeup is lost.
     */
    @volatile protected final var hasWaiters: Boolean = false

    /**
     * The waiter queue, which is also the lock for every field the policy
     * classes mark as guarded. Locking the deque rather than the semaphore
     * keeps monitor traffic off the cache line that holds `hasWaiters`.
     */
    protected final val waiters = new java.util.ArrayDeque[Waiter]

    /** Takes `n` permits now if the implementation's policy allows. */
    protected def tryAcquire(n: Long): Boolean

    /** Takes `n` permits now, returning null, or queues a waiter for them. */
    protected def reserve(n: Long): Waiter

    /** Suspends until the waiter holds its permits. */
    protected def await(waiter: Waiter)(implicit trace: Trace): UIO[Unit]

    /** Returns `n` permits and wakes queued fibers as appropriate. */
    protected def release(n: Long): Unit

    /**
     * Cleans up after a waiter whose owner is done with it: a waiter still
     * queued is removed, one that holds permits returns them.
     */
    protected def cancelOrRelease(waiter: Waiter): Unit

    final def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      withPermits(1L)(zio)

    final def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      withPermitsScoped(1L)

    final def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      if (n < 0L) negative(n)
      else if (n == 0L) zio
      else
        ZIO.uninterruptibleMask { restore =>
          val waiter = reserve(n)
          if (waiter eq null)
            restore(zio).foldCauseZIO(
              cause => { release(n); Exit.failCause(cause) },
              a => { release(n); Exit.succeed(a) }
            )
          else
            restore(await(waiter).flatMap(_ => zio)).foldCauseZIO(
              cause => { cancelOrRelease(waiter); Exit.failCause(cause) },
              a => { release(n); Exit.succeed(a) }
            )
        }

    final def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      if (n < 0L) negative(n)
      else if (n == 0L) ZIO.unit
      else
        ZIO
          .acquireRelease(ZIO.succeed(reserve(n))) { waiter =>
            ZIO.succeed(if (waiter eq null) release(n) else cancelOrRelease(waiter))
          }
          .flatMap(waiter => if (waiter eq null) ZIO.unit else await(waiter))

    final override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit
      trace: Trace
    ): ZIO[R, E, Option[A]] =
      if (n < 0L) negative(n)
      else if (n == 0L) zio.asSome
      else
        ZIO.uninterruptibleMask { restore =>
          if (tryAcquire(n))
            restore(zio).foldCauseZIO(
              cause => { release(n); Exit.failCause(cause) },
              a => { release(n); Exit.succeed(Some(a)) }
            )
          else Exit.none
        }

    private def negative(n: Long)(implicit trace: Trace): UIO[Nothing] =
      ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))

    /** Takes `n` permits if available, ignoring any waiters. */
    @tailrec
    protected final def takePermits(n: Long): Boolean = {
      val current = permits.get
      if (current < n) false
      else if (permits.compareAndSet(current, current - n)) true
      else takePermits(n)
    }

    /**
     * Suspends the fiber until the semaphore wakes the waiter. If the fiber is
     * interrupted first, the wakeup may still arrive and is then dropped by the
     * runtime, so the waiter's owner must call `cancelOrRelease` afterwards.
     */
    protected final def suspend(waiter: Waiter)(implicit trace: Trace): UIO[Unit] =
      ZIO.Async[Any, Nothing, Unit](
        trace,
        callback => {
          val done = waiters.synchronized {
            if (waiter.woken) true
            else {
              waiter.callback = callback
              false
            }
          }
          // A null result registers no interrupt handler, see above.
          if (done) Semaphore.resumeNow else null
        },
        Semaphore.noFiber
      )
  }

  /**
   * The FIFO semaphore. `hasWaiters` gates the fast path, so once a fiber is
   * queued every later arrival queues behind it, and a release hands permits to
   * the head of the queue directly.
   */
  private final class Fair(initial: Long) extends Base(initial) {

    def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(if (hasWaiters) 0L else permits.get)

    override def awaiting(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(if (hasWaiters) waiters.synchronized(waiters.size.toLong) else 0L)

    @tailrec
    protected def tryAcquire(n: Long): Boolean = {
      val current = permits.get
      if (hasWaiters || current < n) false
      else if (permits.compareAndSet(current, current - n)) true
      else tryAcquire(n)
    }

    protected def reserve(n: Long): Waiter =
      if (tryAcquire(n)) null
      else
        waiters.synchronized {
          // Publish the flag before re-reading the permits (see `hasWaiters`).
          hasWaiters = true
          if (waiters.isEmpty && takePermits(n)) {
            hasWaiters = false
            null
          } else {
            val waiter = new Waiter(n)
            waiters.addLast(waiter)
            waiter
          }
        }

    /** A fair waiter is woken only once it holds its permits. */
    protected def await(waiter: Waiter)(implicit trace: Trace): UIO[Unit] =
      suspend(waiter)

    protected def release(n: Long): Unit = {
      permits.addAndGet(n)
      if (hasWaiters) drain()
    }

    protected def cancelOrRelease(waiter: Waiter): Unit = {
      val holding = waiters.synchronized {
        if (waiter.holding) true
        else {
          waiters.remove(waiter)
          if (waiters.isEmpty) hasWaiters = false
          false
        }
      }
      if (holding) release(waiter.n)
      else if (hasWaiters) drain() // the removed waiter may have been blocking smaller requests
    }

    /** Grants as many queued fibers as the free permits satisfy, in order. */
    @tailrec
    private def drain(): Unit = {
      var granted = false
      // The callback is read under the lock: a waiter that registers after this
      // sees `woken` and resumes itself instead.
      val callback = waiters.synchronized {
        val head = waiters.peekFirst
        if ((head ne null) && takePermits(head.n)) {
          waiters.pollFirst()
          head.holding = true
          head.woken = true
          granted = true
          if (waiters.isEmpty) hasWaiters = false
          head.callback
        } else null
      }
      if (granted) {
        if (callback ne null) callback(Exit.unit)
        drain()
      }
    }
  }

  /**
   * The unfair semaphore. The fast path ignores waiters, so a fiber that
   * releases and re-acquires keeps running. A release wakes the head waiter to
   * retry, but only if no woken waiter is already retrying, so a fiber that
   * keeps the permits hot pays for at most one wakeup at a time. A waiter that
   * loses its retry goes back to the front of the queue.
   */
  private final class Unfair(initial: Long) extends Base(initial) {

    /**
     * Guarded by `waiters`: a waiter has been woken and has not yet retried.
     */
    private[this] var retrying: Waiter = null

    def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(permits.get)

    override def awaiting(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(waiters.synchronized(waiters.size.toLong + (if (retrying eq null) 0L else 1L)))

    protected def tryAcquire(n: Long): Boolean = takePermits(n)

    protected def reserve(n: Long): Waiter =
      if (takePermits(n)) null
      else waiters.synchronized(enqueue(new Waiter(n), atFront = false))

    /**
     * Queues the waiter unless the permits arrived meanwhile. The caller holds
     * the lock.
     */
    private def enqueue(waiter: Waiter, atFront: Boolean): Waiter = {
      // Publish the flag before re-reading the permits (see `hasWaiters`).
      hasWaiters = true
      if (takePermits(waiter.n)) {
        waiter.holding = true
        if (waiters.isEmpty) hasWaiters = false
        null
      } else {
        if (atFront) waiters.addFirst(waiter) else waiters.addLast(waiter)
        waiter
      }
    }

    /**
     * Sleeps until woken, then retries; a lost retry re-queues at the front and
     * sleeps again.
     */
    protected def await(waiter: Waiter)(implicit trace: Trace): UIO[Unit] =
      suspend(waiter).flatMap { _ =>
        val acquired = waiters.synchronized {
          retrying = null
          waiter.woken = false
          if (takePermits(waiter.n)) {
            waiter.holding = true
            true
          } else {
            enqueue(waiter, atFront = true) eq null
          }
        }
        if (acquired) ZIO.unit else await(waiter)
      }

    protected def release(n: Long): Unit = {
      permits.addAndGet(n)
      if (hasWaiters) wakeHead()
    }

    protected def cancelOrRelease(waiter: Waiter): Unit = {
      val holding = waiters.synchronized {
        if (waiter.holding) true
        else {
          if (retrying eq waiter) retrying = null
          waiters.remove(waiter)
          if (waiters.isEmpty) hasWaiters = false
          false
        }
      }
      if (holding) release(waiter.n)
      else if (hasWaiters) wakeHead()
    }

    /** Wakes the head waiter to retry unless one is already retrying. */
    private def wakeHead(): Unit = {
      val callback = waiters.synchronized {
        if (retrying ne null) null
        else {
          val head = waiters.pollFirst()
          if (head eq null) null
          else {
            retrying = head
            head.woken = true
            if (waiters.isEmpty) hasWaiters = false
            head.callback
          }
        }
      }
      if (callback ne null) callback(Exit.unit)
    }
  }
}
