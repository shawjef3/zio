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
import zio.internal.SemaphorePermits
import zio.stm.TSemaphore

import scala.annotation.tailrec
import scala.collection.mutable

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
   * A fiber that finds free permits takes them, whether or not other fibers are
   * queued for them. A fiber that releases and immediately re-acquires
   * therefore usually keeps its permits without suspending, which gives higher
   * throughput under contention than [[make]]. A queued fiber may wait
   * indefinitely.
   */
  def makeUnfair(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.makeUnfair(permits)(Unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new Fair(permits)

    def makeUnfair(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new Unfair(permits)
  }

  /**
   * A fiber waiting for `n` permits. All fields are guarded by the owning
   * semaphore's lock. `woken` is set when the semaphore hands the waiter its
   * resumption, either because it was granted its permits (fair) or because it
   * should retry for them (unfair); `holding` is set once the waiter owns its
   * permits; `cancelled` is set by the first `cancelOrRelease`, which makes
   * later ones no-ops and stops the waiter from taking permits.
   */
  private final class Waiter(val n: Long) {
    var callback: ZIO[Any, Nothing, Unit] => Unit = null
    var woken: Boolean                            = false
    var holding: Boolean                          = false
    var cancelled: Boolean                        = false
  }

  /**
   * What both policies share: the permit counter, the waiter queue and the
   * `withPermits` family.
   */
  private sealed abstract class Base(initial: Long) extends SemaphorePermits(initial) with Semaphore {

    /**
     * True whenever `waiters` may be non-empty. Written under the lock, read
     * without it. A waiter publishes `true` before re-reading `permits`, and a
     * releaser adds to `permits` before reading this flag, so one of the two
     * always observes the other and no wakeup is lost.
     */
    @volatile protected final var hasWaiters: Boolean = false

    /**
     * Guarded by `this`, as is every field the policy classes mark as guarded.
     * Allocated when a fiber first queues, so a semaphore that is never
     * contended never pays for it.
     */
    private[this] var queue: mutable.Queue[Waiter] = null

    /** The waiter queue. The caller holds the lock. */
    protected final def waiters: mutable.Queue[Waiter] = {
      if (queue eq null) queue = new mutable.Queue[Waiter]
      queue
    }

    /** The number of queued waiters. The caller holds the lock. */
    protected final def queued: Long =
      if (queue eq null) 0L else queue.size.toLong

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
          val body   = if (waiter eq null) zio else await(waiter).flatMap(_ => zio)
          restore(body).foldCauseZIO(
            cause => { releaseOrCancel(waiter, n); Exit.failCause(cause) },
            a => { release(n); Exit.succeed(a) }
          )
        }

    final def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      if (n < 0L) negative(n)
      else if (n == 0L) ZIO.unit
      else
        ZIO.uninterruptibleMask { restore =>
          ZIO
            .acquireRelease(ZIO.succeed(reserve(n)))(waiter => ZIO.succeed(releaseOrCancel(waiter, n)))
            .flatMap { waiter =>
              // The scope may outlive the wait, so a fiber that stops waiting
              // cleans up now rather than leaving its waiter for the finalizer.
              // Only the wait is interruptible, so there is no point at which
              // a waiter exists without this cleanup in place.
              if (waiter eq null) ZIO.unit
              else restore(await(waiter)).onInterrupt(ZIO.succeed(cancelOrRelease(waiter)))
            }
        }

    /**
     * Undoes a `reserve`: returns the permits it took, or cleans up its waiter.
     */
    private def releaseOrCancel(waiter: Waiter, n: Long): Unit =
      if (waiter eq null) release(n) else cancelOrRelease(waiter)

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
      val current = permitsGet()
      if (current < n) false
      else if (permitsCompareAndSet(current, current - n)) true
      else takePermits(n)
    }

    /**
     * Suspends until the semaphore wakes the waiter. A wakeup that races with
     * interruption is dropped, so the owner must call `cancelOrRelease`
     * afterwards.
     */
    protected final def suspend(waiter: Waiter)(implicit trace: Trace): UIO[Unit] =
      ZIO.asyncMaybe[Any, Nothing, Unit] { callback =>
        val done = synchronized {
          if (waiter.woken) true
          else {
            waiter.callback = callback
            false
          }
        }
        // No interrupt handler: `cancelOrRelease` is the cleanup.
        if (done) Some(Exit.unit) else None
      }
  }

  /**
   * The FIFO semaphore. `hasWaiters` gates the fast path, so once a fiber is
   * queued every later arrival queues behind it, and a release hands permits to
   * the head of the queue directly.
   */
  private final class Fair(initial: Long) extends Base(initial) {

    def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(if (hasWaiters) 0L else permitsGet())

    override def awaiting(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(if (hasWaiters) synchronized(queued) else 0L)

    protected def tryAcquire(n: Long): Boolean =
      !hasWaiters && takePermits(n)

    protected def reserve(n: Long): Waiter =
      if (tryAcquire(n)) null
      else
        synchronized {
          // Publish the flag before re-reading the permits (see `hasWaiters`).
          hasWaiters = true
          if (queued == 0L && takePermits(n)) {
            hasWaiters = false
            null
          } else {
            val waiter = new Waiter(n)
            waiters += waiter
            waiter
          }
        }

    /** A fair waiter is woken only once it holds its permits. */
    protected def await(waiter: Waiter)(implicit trace: Trace): UIO[Unit] =
      suspend(waiter)

    protected def release(n: Long): Unit = {
      permitsAddAndGet(n)
      if (hasWaiters) drain()
    }

    protected def cancelOrRelease(waiter: Waiter): Unit = {
      var first   = false
      var holding = false
      synchronized {
        if (!waiter.cancelled) {
          waiter.cancelled = true
          first = true
          holding = waiter.holding
          if (!holding) {
            waiters.dequeueFirst(_ eq waiter)
            if (queued == 0L) hasWaiters = false
          }
        }
      }
      if (first) {
        if (holding) release(waiter.n)
        else if (hasWaiters) drain() // the removed waiter may have been blocking smaller requests
      }
    }

    /** Grants as many queued fibers as the free permits satisfy, in order. */
    @tailrec
    private def drain(): Unit = {
      var granted = false
      // The callback is read under the lock: a waiter that registers after this
      // sees `woken` and resumes itself instead.
      val callback = synchronized {
        if (waiters.nonEmpty && takePermits(waiters.head.n)) {
          val head = waiters.dequeue()
          head.holding = true
          head.woken = true
          granted = true
          if (queued == 0L) hasWaiters = false
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
   * releases and re-acquires keeps running. A release wakes one waiter to
   * retry, but only if no woken waiter is already retrying, so a fiber that
   * keeps the permits hot pays for at most one wakeup at a time. A waiter whose
   * retry resolves wakes the next, and one that loses goes back to the front of
   * the queue.
   */
  private final class Unfair(initial: Long) extends Base(initial) {

    /**
     * Guarded by `this`: a waiter has been woken and has not yet retried.
     */
    private[this] var retrying: Waiter = null

    def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(permitsGet())

    override def awaiting(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(synchronized(queued + (if (retrying eq null) 0L else 1L)))

    protected def tryAcquire(n: Long): Boolean = takePermits(n)

    protected def reserve(n: Long): Waiter =
      if (takePermits(n)) null
      else synchronized(enqueue(new Waiter(n), atFront = false))

    /**
     * Queues the waiter unless the permits arrived meanwhile. The caller holds
     * the lock.
     */
    private def enqueue(waiter: Waiter, atFront: Boolean): Waiter = {
      // Publish the flag before re-reading the permits (see `hasWaiters`).
      hasWaiters = true
      if (takePermits(waiter.n)) {
        waiter.holding = true
        if (queued == 0L) hasWaiters = false
        null
      } else {
        if (atFront) waiter +=: waiters else waiters += waiter
        waiter
      }
    }

    /**
     * Sleeps until woken, then retries; a lost retry re-queues at the front and
     * sleeps again.
     */
    protected def await(waiter: Waiter)(implicit trace: Trace): UIO[Unit] =
      suspend(waiter).flatMap { _ =>
        var cancelled = false
        val acquired = synchronized {
          if (retrying eq waiter) retrying = null
          if (waiter.cancelled) {
            // Its owner has already cleaned up, so nothing would return permits
            // taken now.
            cancelled = true
            false
          } else {
            waiter.woken = false
            waiter.callback = null
            if (takePermits(waiter.n)) {
              waiter.holding = true
              true
            } else {
              enqueue(waiter, atFront = true) eq null
            }
          }
        }
        // Releases that arrived while this waiter was retrying woke nobody, so
        // pass the wakeup on: permits may be left for another waiter.
        if (hasWaiters) wakeOne()
        if (acquired) ZIO.unit
        else if (cancelled) ZIO.never // as a cancelled fair waiter, which is never woken
        else await(waiter)
      }

    protected def release(n: Long): Unit = {
      permitsAddAndGet(n)
      if (hasWaiters) wakeOne()
    }

    protected def cancelOrRelease(waiter: Waiter): Unit = {
      var first   = false
      var holding = false
      synchronized {
        if (!waiter.cancelled) {
          waiter.cancelled = true
          first = true
          holding = waiter.holding
          if (!holding) {
            if (retrying eq waiter) retrying = null
            waiters.dequeueFirst(_ eq waiter)
            if (queued == 0L) hasWaiters = false
          }
        }
      }
      if (first) {
        if (holding) release(waiter.n)
        else if (hasWaiters) wakeOne()
      }
    }

    /**
     * Wakes the first queued waiter that the free permits can satisfy, unless a
     * waiter is already retrying; that waiter calls this again once its retry
     * resolves, so the queue keeps draining while permits are free.
     */
    private def wakeOne(): Unit = {
      val callback = synchronized {
        val free = permitsGet()
        if ((retrying ne null) || free <= 0L) null
        else
          waiters.dequeueFirst(_.n <= free) match {
            case Some(found) =>
              retrying = found
              found.woken = true
              if (queued == 0L) hasWaiters = false
              found.callback
            case None => null
          }
      }
      if (callback ne null) callback(Exit.unit)
    }
  }
}
