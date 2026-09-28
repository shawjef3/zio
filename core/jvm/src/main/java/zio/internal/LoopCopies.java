/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Experiment (zio/zio#11251): assigns each "program signature" class (the first
 * application lambda on a fiber's initial effect) one of {@link #COPIES}
 * identical copies of {@code FiberRuntime.runLoopInner}, first come first
 * served, so that fibers running different programs profile different copies
 * and the hot lambda of one program is not evicted from the type-profile rows
 * by another program's startup.
 */
public final class LoopCopies {
  private LoopCopies() {}

  public static final int COPIES = 8;

  private static final AtomicInteger next = new AtomicInteger(0);

  private static final ClassValue<Integer> COPY = new ClassValue<Integer>() {
    @Override
    protected Integer computeValue(Class<?> c) {
      return next.getAndIncrement();
    }
  };

  private static final java.util.concurrent.ConcurrentHashMap<Long, Integer> SIGNATURES =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final AtomicInteger nextCopy = new AtomicInteger(0);

  /**
   * Maps a signature (the registration indexes of up to three application lambda classes, -1
   * when absent) to a copy in 1..COPIES-1, first come first served, wrapping when all are
   * taken. Copy 0 is for fibers with no application lambda.
   */
  public static int copyFor(int i1, int i2, int i3) {
    long key = ((long) (i1 & 0x1FFFFF) << 42) | ((long) (i2 & 0x1FFFFF) << 21) | (long) (i3 & 0x1FFFFF);
    Integer c = SIGNATURES.get(key);
    if (c != null) return c;
    return SIGNATURES.computeIfAbsent(key, k -> 1 + nextCopy.getAndIncrement() % (COPIES - 1));
  }

  /** True for lambdas belonging to ZIO's own machinery rather than the application. */
  public static boolean isInternal(Class<?> c) {
    String n = c.getName();
    return n.startsWith("zio.ZIO$") || n.startsWith("zio.internal.") || n.startsWith("zio.Fiber")
        || n.startsWith("zio.Runtime") || n.startsWith("zio.Exit") || n.startsWith("zio.Scope")
        || n.startsWith("zio.Promise") || n.startsWith("zio.Ref") || n.startsWith("zio.Unsafe");
  }

  public static int copyOf(Class<?> c) {
    return COPY.get(c);
  }
}
