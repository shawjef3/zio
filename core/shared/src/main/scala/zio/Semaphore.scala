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
   * Creates a new `Semaphore` with the specified number of permits.
   */
  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.make(permits)(Unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new Impl(permits)
  }

  /**
   * A fiber waiting for `n` permits. The waiter is granted under the
   * semaphore's lock, which sets `granted` and hands the fiber's resumption
   * callback back to the granter; a waiter that is interrupted before being
   * granted is removed from the queue under the same lock.
   */
  private val resumeNow: Either[Nothing, Exit[Nothing, Unit]] = Right(Exit.unit)
  private val noFiber: () => FiberId                          = () => FiberId.None

  private final class Waiter(val n: Long) {
    var callback: ZIO[Any, Nothing, Unit] => Unit = null
    var granted: Boolean                          = false
  }

  private final class Impl(initial: Long) extends Semaphore {

    /**
     * Permits not held by anyone. Acquired on the fast path with a single CAS
     * when `hasWaiters` is false; released with a single atomic add.
     */
    private[this] val permits = new java.util.concurrent.atomic.AtomicLong(initial)

    /**
     * True whenever `waiters` may be non-empty. Written under the lock, read
     * without it: an acquirer that reads `false` may take permits directly, one
     * that reads `true` must queue behind the existing waiters, which keeps the
     * semaphore FIFO. A waiter publishes `true` before re-reading `permits`,
     * and a releaser adds to `permits` before reading this flag, so one of the
     * two always observes the other and no wakeup is lost.
     */
    @volatile private[this] var hasWaiters: Boolean = false

    /** Guarded by `this`. */
    private[this] val waiters = new java.util.ArrayDeque[Waiter]

    def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(if (hasWaiters) 0L else permits.get)

    override def awaiting(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(if (hasWaiters) synchronized(waiters.size.toLong) else 0L)

    def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      withPermits(1L)(zio)

    def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      withPermitsScoped(1L)

    def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      if (n < 0L) negative(n)
      else if (n == 0L) zio
      else
        ZIO.suspendSucceed {
          val waiter = reserve(n)
          if (waiter eq null)
            zio.foldCauseZIO(
              cause => { release(n); Exit.failCause(cause) },
              a => { release(n); Exit.succeed(a) }
            )
          else
            restore(await(waiter).flatMap(_ => zio)).foldCauseZIO(
              cause => { cancelOrRelease(waiter); Exit.failCause(cause) },
              a => { release(n); Exit.succeed(a) }
            )
        }

    def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      if (n < 0L) negative(n)
      else if (n == 0L) ZIO.unit
      else
        ZIO
          .acquireRelease(ZIO.succeed(reserve(n))) { waiter =>
            ZIO.succeed(if (waiter eq null) release(n) else cancelOrRelease(waiter))
          }
          .flatMap(waiter => if (waiter eq null) ZIO.unit else await(waiter))

    override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
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

    /**
     * Takes `n` permits if they are available and nobody is queued ahead.
     */
    @tailrec
    private def tryAcquire(n: Long): Boolean = {
      val current = permits.get
      if (hasWaiters || current < n) false
      else if (permits.compareAndSet(current, current - n)) true
      else tryAcquire(n)
    }

    /**
     * Takes `n` permits now, returning null, or queues a waiter for them.
     */
    private def reserve(n: Long): Waiter =
      if (tryAcquire(n)) null
      else
        synchronized {
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

    /** Takes `n` permits if available, regardless of `hasWaiters`. */
    @tailrec
    private def takePermits(n: Long): Boolean = {
      val current = permits.get
      if (current < n) false
      else if (permits.compareAndSet(current, current - n)) true
      else takePermits(n)
    }

    /**
     * Suspends the fiber until the waiter is granted. If the fiber is
     * interrupted first, the grant may still arrive and is then dropped by the
     * runtime, so the waiter's owner must call `cancelOrRelease` afterwards.
     */
    private def await(waiter: Waiter)(implicit trace: Trace): UIO[Unit] =
      ZIO.Async[Any, Nothing, Unit](
        trace,
        callback => {
          val done = synchronized {
            if (waiter.granted) true
            else {
              waiter.callback = callback
              false
            }
          }
          // A null result registers no interrupt handler: an interrupted waiter is
          // cleaned up by its owner through `cancelOrRelease`, and a grant that
          // races with the interrupt is dropped by the runtime.
          if (done) Semaphore.resumeNow else null
        },
        Semaphore.noFiber
      )

    /**
     * Returns `n` permits and wakes as many queued fibers as they satisfy, in
     * FIFO order.
     */
    private def release(n: Long): Unit = {
      permits.addAndGet(n)
      if (hasWaiters) drain()
    }

    /**
     * Removes a waiter that was never granted, or returns its permits if it
     * was.
     */
    private def cancelOrRelease(waiter: Waiter): Unit = {
      val granted = synchronized {
        if (waiter.granted) true
        else {
          waiters.remove(waiter)
          if (waiters.isEmpty) hasWaiters = false
          false
        }
      }
      if (granted) release(waiter.n)
      else if (hasWaiters) drain() // the removed waiter may have been blocking smaller requests
    }

    @tailrec
    private def drain(): Unit = {
      var granted = false
      // The callback is read under the lock: a waiter that registers after this
      // sees `granted` and resumes itself instead.
      val callback = synchronized {
        val head = waiters.peekFirst
        if ((head ne null) && takePermits(head.n)) {
          waiters.pollFirst()
          head.granted = true
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
}
