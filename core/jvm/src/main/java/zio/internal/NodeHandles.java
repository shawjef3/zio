package zio.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Experiment for zio/zio#11251, shared-handle variant: `FiberRuntime.runLoop`
 * invokes the lambdas of `Sync` and `FlatMap` nodes through one of two shared,
 * unbound `MethodHandle`s, passing the lambda as the receiver. The handles are
 * held in non-final static fields, which C2 does not treat as constants, so the
 * target is opaque and never inlined into the caller. Unlike binding a handle
 * per node, this costs nothing at node construction, so comparing the two
 * separates construction cost from dispatch cost.
 *
 * Written in Java because Scala 2.13 cannot emit a signature-polymorphic
 * `invokeExact` call with an exact erased descriptor.
 */
final class NodeHandles {
  private NodeHandles() {}

  /** `(Function0)Object`. Deliberately not final: see the class comment. */
  private static MethodHandle apply0;

  /** `(Function1,Object)Object`. Deliberately not final: see the class comment. */
  private static MethodHandle apply1;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      apply0 = lookup.findVirtual(scala.Function0.class, "apply", MethodType.methodType(Object.class));
      apply1 =
          lookup.findVirtual(
              scala.Function1.class, "apply", MethodType.methodType(Object.class, Object.class));
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  static Object invoke0(scala.Function0<?> f) throws Throwable {
    return (Object) apply0.invokeExact(f);
  }

  static Object invoke1(scala.Function1<?, ?> f, Object a) throws Throwable {
    return (Object) apply1.invokeExact(f, a);
  }
}
