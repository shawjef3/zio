package zio.internal

/**
 * Platform shim for the method-handle dispatch experiment (zio/zio#11251). Off
 * the JVM there are no method handles, so a handle is the function itself.
 */
private[zio] object NodeDispatch {
  type Handle0 = () => Any
  type Handle1 = Any => Any

  @inline def bind0(f: () => Any): Handle0     = f
  @inline def bind1(f: Any => Any): Handle1    = f
  @inline def invoke0(h: Handle0): Any         = h()
  @inline def invoke1(h: Handle1, a: Any): Any = h(a)

  // Stack frames from here down belong to the run loop and are trimmed from traces.
  def isRunLoopFrame(className: String): Boolean =
    className == "zio.internal.FiberRuntime" || className == "zio.internal.NodeHandles" ||
      className == "zio.internal.NodeDispatch$"
}
