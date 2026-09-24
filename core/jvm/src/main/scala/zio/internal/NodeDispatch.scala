package zio.internal

/**
 * Platform shim for the method-handle dispatch experiment (zio/zio#11251). On
 * the JVM a handle is a bound `MethodHandle` invoked with `invokeExact`.
 */
private[zio] object NodeDispatch {
  type Handle0 = java.lang.invoke.MethodHandle
  type Handle1 = java.lang.invoke.MethodHandle

  def bind0(f: () => Any): Handle0     = NodeHandles.bind0(f)
  def bind1(f: Any => Any): Handle1    = NodeHandles.bind1(f)
  def invoke0(h: Handle0): Any         = NodeHandles.invoke0(h)
  def invoke1(h: Handle1, a: Any): Any = NodeHandles.invoke1(h, a.asInstanceOf[AnyRef])

  // Stack frames from here down belong to the run loop and are trimmed from traces.
  def isRunLoopFrame(className: String): Boolean =
    className == "zio.internal.FiberRuntime" || className == "zio.internal.NodeHandles" ||
      className == "zio.internal.NodeDispatch$"
}
