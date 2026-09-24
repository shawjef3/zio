package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

/**
 * `LeftBindBenchmark.zioLeftBindBenchmark`, unchanged, except that trial setup
 * first runs [[RunLoopPoison]] so the lambda sites in `runLoop` are megamorphic
 * before C2 compiles it (zio/zio#11251).
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class LeftBindPoisonBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  @Param(Array("100"))
  var depth: Int = _

  @Setup(Level.Trial)
  def setup(): Unit = RunLoopPoison.run()

  @Benchmark
  def zioLeftBindBenchmark: Int = zioLeftBindBenchmark(BenchmarkUtil)

  private[this] def zioLeftBindBenchmark(runtime: Runtime[Any]): Int = {
    def loop(i: Int): UIO[Int] =
      if (i % depth == 0) ZIO.succeed[Int](i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => ZIO.succeed(i))
      else ZIO.succeed(i)

    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(ZIO.succeed(0).flatMap[Any, Nothing, Int](loop)).getOrThrowFiberFailure()
    }
  }
}
