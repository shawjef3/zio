package zio

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object SemaphoreSpec extends ZIOBaseSpec {
  override def spec = suite("SemaphoreSpec")(
    test("withPermit automatically releases the permit if the effect is interrupted") {
      for {
        promise   <- Promise.make[Nothing, Unit]
        semaphore <- Semaphore.make(1)
        effect     = semaphore.withPermit(promise.succeed(()) *> ZIO.never)
        fiber     <- effect.fork
        _         <- promise.await
        _         <- fiber.interrupt
        permits   <- semaphore.available
      } yield assert(permits)(equalTo(1L))
    },
    test("withPermit acquire is interruptible") {
      for {
        semaphore <- Semaphore.make(0L)
        effect     = semaphore.withPermit(ZIO.unit)
        fiber     <- effect.fork
        _         <- fiber.interrupt
      } yield assertCompletes
    },
    test("withPermitsScoped releases same number of permits") {
      for {
        semaphore <- Semaphore.make(2L)
        _         <- ZIO.scoped(semaphore.withPermitsScoped(2))
        permits   <- semaphore.available
      } yield assertTrue(permits == 2L)
    },
    test("tryWithPermits acquires and releases same number of permits") {
      for {
        sem     <- Semaphore.make(3L)
        ans     <- sem.tryWithPermits(2L)(ZIO.unit)
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.isDefined)
    },
    test("tryWithPermits if 0 permits requested") {
      for {
        sem     <- Semaphore.make(3L)
        ans     <- sem.tryWithPermits(0L)(ZIO.succeed("I got executed"))
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.contains("I got executed"))
    },
    test("tryWithPermits returns None if no permits available") {
      for {
        sem     <- Semaphore.make(3L)
        ans     <- sem.tryWithPermits(4L)(ZIO.succeed("Shouldn't get executed"))
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.isEmpty)
    },
    test("tryWithPermit acquires and releases same number of permits") {
      for {
        sem     <- Semaphore.make(3L)
        ans     <- sem.tryWithPermit(ZIO.unit)
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.isDefined)
    },
    test("tryWithPermits fails if requested permits in negative number") {
      for {
        sem <- Semaphore.make(3L)
        ans <- sem.tryWithPermits(-1L)(ZIO.unit).exit
      } yield assert(ans)(dies(isSubtype[IllegalArgumentException](anything)))
    },
    test("tryWithPermits restores permits after failure") {
      for {
        sem     <- Semaphore.make(3L)
        failure  = ZIO.fail("exception")
        result  <- sem.tryWithPermits(2L)(failure).exit
        permits <- sem.available
      } yield assertTrue(
        permits == 3L,
        result.isFailure,
        result == Exit.fail("exception")
      )
    },
    test("awaiting returns the count of waiting fibers") {
      for {
        semaphore    <- Semaphore.make(1)
        promise      <- Promise.make[Nothing, Unit]
        _            <- ZIO.foreachDiscard(1 to 11)(_ => semaphore.withPermit(promise.await).fork)
        waitingStart <- semaphore.awaiting.repeatUntil(_ == 10)
        _            <- promise.succeed(())
        waitingEnd   <- semaphore.awaiting.repeatUntil(_ == 0)
      } yield assertTrue(waitingStart == 10, waitingEnd == 0)
    } @@ timeout(10.seconds),
    test("withPermit provides mutual exclusion under contention") {
      val active    = new java.util.concurrent.atomic.AtomicInteger(0)
      val violation = new java.util.concurrent.atomic.AtomicInteger(0)
      val body = ZIO.succeed {
        if (active.incrementAndGet() > 1) violation.incrementAndGet()
        active.decrementAndGet()
      }
      for {
        sem <- Semaphore.make(1L)
        _   <- ZIO.foreachParDiscard(1 to 10)(_ => sem.withPermit(body).repeatN(999))
      } yield assertTrue(violation.get == 0)
    } @@ timeout(30.seconds),
    test("withPermits never over-allocates permits under contention") {
      val active    = new java.util.concurrent.atomic.AtomicInteger(0)
      val violation = new java.util.concurrent.atomic.AtomicInteger(0)
      def body(n: Int) = ZIO.succeed {
        if (active.addAndGet(n) > 5) violation.incrementAndGet()
        active.addAndGet(-n)
      }
      for {
        sem <- Semaphore.make(5L)
        _   <- ZIO.foreachParDiscard(1 to 10)(i => sem.withPermits((i % 5 + 1).toLong)(body(i % 5 + 1)).repeatN(499))
      } yield assertTrue(violation.get == 0)
    } @@ timeout(30.seconds),
    test("withPermits waits for all requested permits and releases them") {
      for {
        sem      <- Semaphore.make(2L)
        gate     <- Promise.make[Nothing, Unit]
        holder   <- sem.withPermits(2L)(gate.await).fork
        _        <- sem.available.repeatUntil(_ == 0L)
        waiter   <- sem.withPermits(2L)(ZIO.unit).fork
        _        <- sem.awaiting.repeatUntil(_ == 1L)
        before   <- sem.available
        _        <- gate.succeed(())
        _        <- holder.join
        _        <- waiter.join
        after    <- sem.available
        awaiting <- sem.awaiting
      } yield assertTrue(before == 0L, after == 2L, awaiting == 0L)
    } @@ timeout(10.seconds),
    test("waiting fibers are granted permits in FIFO order") {
      for {
        sem    <- Semaphore.make(1L)
        gate   <- Promise.make[Nothing, Unit]
        order  <- Ref.make(List.empty[Int])
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        fibers <- ZIO.foreach(1 to 5) { i =>
                    sem.withPermit(order.update(i :: _)).fork <* sem.awaiting.repeatUntil(_ == i.toLong)
                  }
        _      <- gate.succeed(())
        _      <- holder.join
        _      <- ZIO.foreachDiscard(fibers)(_.join)
        result <- order.get
      } yield assertTrue(result.reverse == List(1, 2, 3, 4, 5))
    } @@ timeout(10.seconds),
    test("interrupting a waiting fiber removes it from the queue without losing permits") {
      for {
        sem      <- Semaphore.make(1L)
        gate     <- Promise.make[Nothing, Unit]
        holder   <- sem.withPermit(gate.await).fork
        _        <- sem.available.repeatUntil(_ == 0L)
        waiter   <- sem.withPermit(ZIO.unit).fork
        _        <- sem.awaiting.repeatUntil(_ == 1L)
        _        <- waiter.interrupt
        awaiting <- sem.awaiting
        _        <- gate.succeed(())
        _        <- holder.join
        after    <- sem.available
      } yield assertTrue(awaiting == 0L, after == 1L)
    } @@ timeout(10.seconds),
    test("interrupting the head waiter lets later waiters proceed") {
      for {
        sem    <- Semaphore.make(2L)
        gate   <- Promise.make[Nothing, Unit]
        holder <- sem.withPermits(2L)(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        big    <- sem.withPermits(2L)(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        small  <- sem.withPermit(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 2L)
        _      <- big.interrupt
        _      <- gate.succeed(())
        _      <- holder.join
        _      <- small.join
        after  <- sem.available
      } yield assertTrue(after == 2L)
    } @@ timeout(10.seconds),
    test("withPermitScoped is interruptible while waiting and does not leak permits") {
      for {
        sem    <- Semaphore.make(1L)
        gate   <- Promise.make[Nothing, Unit]
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        waiter <- ZIO.scoped(sem.withPermitScoped *> ZIO.never).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        _      <- waiter.interrupt
        _      <- gate.succeed(())
        _      <- holder.join
        after  <- sem.available
      } yield assertTrue(after == 1L)
    } @@ timeout(10.seconds),
    test("tryWithPermits does not jump ahead of waiting fibers") {
      for {
        sem    <- Semaphore.make(3L)
        gate   <- Promise.make[Nothing, Unit]
        holder <- sem.withPermits(2L)(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 1L)
        waiter <- sem.withPermits(2L)(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        tried  <- sem.tryWithPermit(ZIO.unit)
        _      <- gate.succeed(())
        _      <- holder.join
        _      <- waiter.join
        after  <- sem.available
      } yield assertTrue(tried.isEmpty, after == 3L)
    } @@ timeout(10.seconds)
  ) @@ exceptJS(nonFlaky)
}
