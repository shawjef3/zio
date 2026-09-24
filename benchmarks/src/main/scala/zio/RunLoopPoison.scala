package zio

/**
 * Pollutes the type profiles of the lambda call sites in `FiberRuntime.runLoop`
 * before C2 compiles it (zio/zio#11251).
 *
 * Three textually distinct programs, so three distinct lambda classes, each
 * pass through every shared site: the `Sync` thunk, the `FlatMap`, `FoldZIO`
 * (success and failure) and `Mapped` continuations under both the `Sync` and
 * the `Exit.Success` unwinds, and a `Stateful`. After `run()` each of those
 * sites has seen at least three receivers, so C2 treats it as megamorphic and
 * never inlines an application lambda there, whatever runs later.
 *
 * The programs are written out three times on purpose. Sharing code between
 * them would share lambda classes and defeat the purpose.
 */
object RunLoopPoison {

  // `flatMap(a => Exit.succeed(a))` makes the next frame unwind under the
  // `Exit.Success` case rather than the `Sync` case, so both copies of each
  // continuation site are reached.

  private def program1: UIO[Int] =
    ZIO
      .succeed(1)
      .flatMap(a => ZIO.succeed(a + 1))
      .map(a => a + 1)
      .flatMap(a => ZIO.succeed(a).flatMap(b => Exit.succeed(b + 1)).map(c => c + 1))
      .foldCauseZIO(_ => ZIO.succeed(0), a => ZIO.succeed(a + 1))
      .flatMap(a => ZIO.succeed(a).flatMap(b => Exit.succeed(b)).foldCauseZIO(_ => ZIO.succeed(0), c => ZIO.succeed(c)))
      .flatMap(a => ZIO.fail(a).foldCauseZIO(_ => ZIO.succeed(a + 1), (_: Nothing) => ZIO.succeed(0)))
      .flatMap(a => ZIO.withFiberRuntime[Any, Nothing, Int]((_, _) => ZIO.succeed(a + 1)))

  private def program2: UIO[Int] =
    ZIO
      .succeed(2)
      .flatMap(a => ZIO.succeed(a + 2))
      .map(a => a + 2)
      .flatMap(a => ZIO.succeed(a).flatMap(b => Exit.succeed(b + 2)).map(c => c + 2))
      .foldCauseZIO(_ => ZIO.succeed(0), a => ZIO.succeed(a + 2))
      .flatMap(a => ZIO.succeed(a).flatMap(b => Exit.succeed(b)).foldCauseZIO(_ => ZIO.succeed(0), c => ZIO.succeed(c)))
      .flatMap(a => ZIO.fail(a).foldCauseZIO(_ => ZIO.succeed(a + 2), (_: Nothing) => ZIO.succeed(0)))
      .flatMap(a => ZIO.withFiberRuntime[Any, Nothing, Int]((_, _) => ZIO.succeed(a + 2)))

  private def program3: UIO[Int] =
    ZIO
      .succeed(3)
      .flatMap(a => ZIO.succeed(a + 3))
      .map(a => a + 3)
      .flatMap(a => ZIO.succeed(a).flatMap(b => Exit.succeed(b + 3)).map(c => c + 3))
      .foldCauseZIO(_ => ZIO.succeed(0), a => ZIO.succeed(a + 3))
      .flatMap(a => ZIO.succeed(a).flatMap(b => Exit.succeed(b)).foldCauseZIO(_ => ZIO.succeed(0), c => ZIO.succeed(c)))
      .flatMap(a => ZIO.fail(a).foldCauseZIO(_ => ZIO.succeed(a + 3), (_: Nothing) => ZIO.succeed(0)))
      .flatMap(a => ZIO.withFiberRuntime[Any, Nothing, Int]((_, _) => ZIO.succeed(a + 3)))

  /**
   * Runs each program `rounds` times, interleaved. A few hundred rounds is
   * enough for the interpreter to allocate `runLoop`'s profile and record all
   * three receivers at each site; it takes a few milliseconds.
   */
  def run(rounds: Int = 200): Unit =
    Unsafe.unsafe { implicit unsafe =>
      val p1 = program1
      val p2 = program2
      val p3 = program3
      var i  = 0
      while (i < rounds) {
        BenchmarkUtil.unsafe.run(p1).getOrThrowFiberFailure()
        BenchmarkUtil.unsafe.run(p2).getOrThrowFiberFailure()
        BenchmarkUtil.unsafe.run(p3).getOrThrowFiberFailure()
        i += 1
      }
    }
}
