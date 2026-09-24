package zio.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Experiment for zio/zio#11251: dispatch the lambdas that `FiberRuntime.runLoop`
 * invokes through a per-node `MethodHandle`. A handle held in an instance field
 * is not a compile-time constant, so C2 cannot inline its target into the
 * caller, and application lambdas never reach `runLoop`'s inlining budget.
 *
 * Written in Java because Scala 2.13 cannot emit a signature-polymorphic
 * `invokeExact` call with an exact erased descriptor.
 */
final class NodeHandles {
  private NodeHandles() {}

  /** `(Function0)Object`, bound per node to `()Object`. */
  private static final MethodHandle APPLY0;

  /** `(Function1,Object)Object`, bound per node to `(Object)Object`. */
  private static final MethodHandle APPLY1;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      APPLY0 = lookup.findVirtual(scala.Function0.class, "apply", MethodType.methodType(Object.class));
      APPLY1 =
          lookup.findVirtual(
              scala.Function1.class, "apply", MethodType.methodType(Object.class, Object.class));
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  static MethodHandle bind0(scala.Function0<?> f) {
    return APPLY0.bindTo(f);
  }

  static MethodHandle bind1(scala.Function1<?, ?> f) {
    return APPLY1.bindTo(f);
  }

  static Object invoke0(MethodHandle h) throws Throwable {
    return (Object) h.invokeExact();
  }

  static Object invoke1(MethodHandle h, Object a) throws Throwable {
    return (Object) h.invokeExact(a);
  }
}
