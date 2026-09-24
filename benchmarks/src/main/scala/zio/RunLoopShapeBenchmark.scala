package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

/**
 * Measures how much the compiled shape of `FiberRuntime.runLoop` depends on
 * what the JVM ran before C2 compiled it (zio/zio#11251).
 *
 * The measured program never changes: a narrow `succeed.flatMap` chain of fixed
 * depth. Two things vary, both of which only affect the JIT:
 *
 *   - `startupMix` runs a chosen workload in trial setup, before the first
 *     measured invocation, so each fork's `runLoop` is compiled from that
 *     startup profile. A `runLoop` whose compiled shape does not depend on
 *     startup history scores the same for every value.
 *   - `syncShapes` rotates one, two, or three textually distinct programs
 *     through the measured invocations. Each program's `succeed` thunk and
 *     `flatMap` continuation are distinct lambda classes, so the `Sync` and
 *     `FlatMap` call sites in `runLoop` see one, two, or three receiver
 *     classes. One or two lets C2 inline the application lambda into `runLoop`;
 *     three makes the site megamorphic. The gap between 2 and 3 is the value of
 *     inlining application code into `runLoop`.
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Threads(1)
@Fork(1)
class RunLoopShapeBenchmark {

  @Param(Array("none", "sameProgram", "forks", "failures", "async", "mixed", "poison"))
  var startupMix: String = _

  @Param(Array("1", "2", "3"))
  var syncShapes: Int = _

  private[this] val size = 10000

  private[this] var programs: Array[UIO[Int]] = _
  private[this] var next: Int                 = 0

  // Three programs that do the same work from three distinct call sites, so
  // their thunks and continuations are three distinct lambda classes.
  private[this] def loop1(i: Int): UIO[Int] =
    if (i < size) ZIO.succeed[Int](i + 1).flatMap(loop1) else ZIO.succeed(i)

  private[this] def loop2(i: Int): UIO[Int] =
    if (i < size) ZIO.succeed[Int](i + 1).flatMap(loop2) else ZIO.succeed(i)

  private[this] def loop3(i: Int): UIO[Int] =
    if (i < size) ZIO.succeed[Int](i + 1).flatMap(loop3) else ZIO.succeed(i)

  private[this] val program1: UIO[Int] = ZIO.succeed(0).flatMap(loop1)
  private[this] val program2: UIO[Int] = ZIO.succeed(0).flatMap(loop2)
  private[this] val program3: UIO[Int] = ZIO.succeed(0).flatMap(loop3)

  private[this] def run[A](zio: UIO[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      BenchmarkUtil.unsafe.run(zio).getOrThrowFiberFailure()
    }

  // Startup workloads. Each is one round; setup repeats rounds for a fixed
  // wall-clock budget so the profile C2 sees is dominated by this workload.
  private[this] val forkRound: UIO[Unit] =
    ZIO.unit.fork.flatMap(_.join)

  private[this] val failureRound: UIO[Unit] =
    ZIO.fail("boom").catchAll(_ => ZIO.unit)

  private[this] val asyncRound: UIO[Unit] =
    ZIO.async[Any, Nothing, Unit](cb => cb(ZIO.unit)) *> ZIO.yieldNow

  private[this] def repeatFor(budgetNanos: Long)(round: () => Unit): Unit = {
    val deadline = java.lang.System.nanoTime() + budgetNanos
    while (java.lang.System.nanoTime() < deadline) round()
  }

  @Setup(Level.Trial)
  def setup(): Unit = {
    programs = Array(program1, program2, program3).take(syncShapes)
    next = 0

    val budget = TimeUnit.MILLISECONDS.toNanos(500)

    startupMix match {
      case "none" =>
        ()
      case "poison" =>
        RunLoopPoison.run()
      case "sameProgram" =>
        repeatFor(budget) { () => run(program1); () }
      case "forks" =>
        repeatFor(budget)(() => run(BenchmarkUtil.repeat(100)(forkRound)))
      case "failures" =>
        repeatFor(budget)(() => run(BenchmarkUtil.repeat(1000)(failureRound)))
      case "async" =>
        repeatFor(budget)(() => run(BenchmarkUtil.repeat(1000)(asyncRound)))
      case "mixed" =>
        repeatFor(budget) { () =>
          run(program1)
          run(BenchmarkUtil.repeat(100)(forkRound))
          run(BenchmarkUtil.repeat(1000)(failureRound))
          run(BenchmarkUtil.repeat(1000)(asyncRound))
          ()
        }
      case other =>
        throw new IllegalArgumentException(s"unknown startupMix: $other")
    }
  }

  @Benchmark
  def narrowFlatMap(): Int = {
    val p = programs(next)
    next = if (next + 1 == programs.length) 0 else next + 1
    run(p)
  }
}
