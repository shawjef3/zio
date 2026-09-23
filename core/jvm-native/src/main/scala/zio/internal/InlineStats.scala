package zio.internal

/**
 * TEMPORARY instrumentation for the inline-chain sweep. Not for merge.
 *
 * Counts how often `Executor.claimInlineExecution` grants the inline-resume
 * path and, when it refuses, which of the two gates refused: the cap on chain
 * depth, or the calling thread not being one of this scheduler's workers.
 *
 * `LongAdder` rather than `AtomicLong` so that counting does not add contention
 * to the thing being measured.
 */
object InlineStats {
  val granted          = new java.util.concurrent.atomic.LongAdder
  val refusedAtCap     = new java.util.concurrent.atomic.LongAdder
  val refusedNotWorker = new java.util.concurrent.atomic.LongAdder

  def reset(): Unit = {
    granted.reset(); refusedAtCap.reset(); refusedNotWorker.reset()
  }

  def report(): String = {
    val g = granted.sum(); val c = refusedAtCap.sum(); val n = refusedNotWorker.sum()
    val t = g + c + n
    val pct = if (t == 0L) 0.0 else g.toDouble * 100.0 / t.toDouble
    f"INLINESTATS granted=$g refusedAtCap=$c refusedNotWorker=$n grantedPct=$pct%.2f"
  }
}
