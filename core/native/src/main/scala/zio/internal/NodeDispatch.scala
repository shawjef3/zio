package zio.internal

/**
 * Platform shim for the method-handle dispatch experiment (zio/zio#11251). Off
 * the JVM there are no method handles, so the function is called directly.
 */
private[zio] object NodeDispatch {
  @inline def invoke0(f: () => Any): Any          = f()
  @inline def invoke1(f: Any => Any, a: Any): Any = f(a)

  // Stack frames from here down belong to the run loop and are trimmed from traces.
  def isRunLoopFrame(className: String): Boolean =
    className == "zio.internal.FiberRuntime" || className == "zio.internal.NodeHandles" ||
      className == "zio.internal.NodeDispatch$"
}
