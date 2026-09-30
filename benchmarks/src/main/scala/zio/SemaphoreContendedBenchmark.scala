package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

/**
 * Measures `withPermit` with real suspended fibers, in the regime where permits
 * are scarcer than fibers so that most acquisitions have to queue.
 *
 * This exists because `SemaphorePermitBenchmark` is too noisy to resolve
 * changes to the contended path: its 10-fiber rows come back with error bars
 * around 20%, which is wider than any plausible improvement. Three things are
 * done differently here:
 *
 *   - The effect each fiber runs is built once in `@Setup` rather than inside
 *     the measured op. `repeat(n)` chains `n` effects with `*>`, so building it
 *     per op allocated on the order of a thousand `FlatMap` nodes per fiber and
 *     put that allocation, and the GC it caused, inside the measurement.
 *   - The semaphore is created once per trial rather than per op, so the op is
 *     acquire/release traffic rather than construction.
 *   - The guarded effect is a counter increment rather than a `Blackhole`
 *     consume, so the body is a few nanoseconds and what dominates is the
 *     acquire/release pair and the suspension it causes. The counter is a
 *     `LongAdder`: its cells are padded, so neither the counter's own
 *     contention nor where it happens to sit next to the semaphore shows up in
 *     the score.
 *
 * What remains inside the op is forking the fibers and joining them, which
 * cannot be hoisted: the contention being measured only exists while several
 * fibers are running at once. [[baseline]] measures exactly that much with the
 * semaphore removed, so the difference is what acquisition costs.
 *
 * Almost all of the variance is between JVMs rather than within one: once
 * settled, a fork's iterations agree to about 1%, while forks differ by far
 * more. Iterations therefore do not damp it and forks do. Two effects set a
 * fork's level:
 *
 *   - Heap layout. Which of the semaphore's objects share a cache line is
 *     decided when they are promoted to the old generation, about 15 young
 *     collections in, and then fixed for the life of the JVM. Keep at least 10
 *     one-second warmup iterations, or some forks are measured before they
 *     settle; two measurement iterations are enough after that.
 *   - JIT inlining in the run loop. This benchmark's body and the runtime's own
 *     code share call sites in `FiberRuntime.runLoop`, and in a program this
 *     small C2 inlines at them depending on compile timing. The fork thunk runs
 *     through one such site, and about one JVM in six settles 30% low at one
 *     permit when C2 inlines the fork path there and runs out of budget for the
 *     rest of the loop. If forks split into a fast and a slow group, suspect
 *     that inlining race before anything in the semaphore, and look at the
 *     fraction of slow forks, not only the mean. Do not hide it with JVM flags
 *     such as `-XX:-TieredCompilation`, which measure a configuration
 *     production does not run.
 *
 * Use at least 12 forks, one parameter point per run:
 *
 * {{{
 * benchmarks/jmh:run -f 12 -wi 10 -i 2 -p permits=5 zio.SemaphoreContendedBenchmark
 * }}}
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 2, timeUnit = TimeUnit.SECONDS, time = 1)
@Warmup(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 1)
@Fork(12)
class SemaphoreContendedBenchmark {

  /** Fibers competing for the semaphore. */
  @Param(Array("10"))
  var fibers: Int = _

  /** Permits available; below `fibers`, so most acquisitions have to queue. */
  @Param(Array("1", "2", "5"))
  var permits: Int = _

  /** Acquisitions per fiber per op. */
  final val ops: Int = 1000

  /**
   * Incremented by the guarded effect so that neither the body nor the
   * acquisition can be optimised away, and read in `@TearDown` so the counter
   * itself stays live.
   */
  private[this] val counter = new LongAdder

  private var withSem: List[ZIO[Any, Nothing, Unit]]    = _
  private var withoutSem: List[ZIO[Any, Nothing, Unit]] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val sem  = unsafeRun(Semaphore.make(permits.toLong))
    val body = ZIO.succeed(counter.increment())

    withSem = List.fill(fibers)(repeat(ops)(sem.withPermit(body)))
    withoutSem = List.fill(fibers)(repeat(ops)(body))
  }

  @TearDown(Level.Trial)
  def tearDown(): Unit =
    if (counter.sum() == 0L) throw new AssertionError("benchmark body never ran")

  /** Fibers contending for `permits` permits. */
  @Benchmark
  def contended(): Unit =
    unsafeRun(ZIO.forkAll(withSem).flatMap(_.join).unit)

  /**
   * The same fibers over the same number of effects with no semaphore: the
   * floor imposed by forking, scheduling and joining alone.
   */
  @Benchmark
  def baseline(): Unit =
    unsafeRun(ZIO.forkAll(withoutSem).flatMap(_.join).unit)
}
