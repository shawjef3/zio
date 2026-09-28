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

  /** Combines the registration indexes of up to three signature classes into a copy in 1..COPIES-1; copy 0 is for fibers with no application lambda. */
  public static int copyFor(int i1, int i2, int i3) {
    int h = i1 * 1000003 + i2 * 31 + i3;
    if (i2 < 0) h = i1;
    return 1 + Math.floorMod(h, COPIES - 1);
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
