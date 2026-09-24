package zio.internal

/**
 * Platform shim for the method-handle dispatch experiment (zio/zio#11251). On
 * the JVM the lambda is invoked through a shared, non-constant `MethodHandle`.
 */
private[zio] object NodeDispatch {
  def invoke0(f: () => Any): Any          = NodeHandles.invoke0(f)
  def invoke1(f: Any => Any, a: Any): Any = NodeHandles.invoke1(f, a.asInstanceOf[AnyRef])

  // Stack frames from here down belong to the run loop and are trimmed from traces.
  def isRunLoopFrame(className: String): Boolean =
    className == "zio.internal.FiberRuntime" || className == "zio.internal.NodeHandles" ||
      className == "zio.internal.NodeDispatch$"
}
