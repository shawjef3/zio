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
 * Assigns each lambda class that reaches {@code runLoop}'s {@code Sync} and
 * {@code FlatMap} call sites one of {@link #SLOTS} call sites (zio/zio#11251).
 *
 * <p>HotSpot records two receiver classes per call site and inlines the
 * dominant one; which classes win the two rows is a startup race, so whether
 * the hot lambda is inlined varies between JVM starts. Routing each class to
 * its own {@code switch} arm makes every arm monomorphic, so C2 inlines it
 * whatever else ran first. Slots {@code 0..FIRST_COME-1} go to the first
 * classes seen; the rest are reserved for classes that prove hot on the
 * shared overflow arm (see {@link #promote}), so a late-constructed hot
 * lambda still gets its own site.
 */
public final class SyncSlots {

  private SyncSlots() {}

  /** Number of dedicated arms in each switch in {@code FiberRuntime.runLoop}. */
  public static final int SLOTS = 16;
  /** Slots handed out in first-seen order; the rest are reserved for promotion. */
  public static final int FIRST_COME = 8;
  /** The shared arm: a plain virtual call, as today. */
  public static final int OVERFLOW = SLOTS;
  /** Overflow calls a class makes before it is promoted to a reserved slot. */
  public static final long PROMOTE_AFTER = 1L << 14;

  public static final class Entry {
    public volatile int slot;
    /** Overflow calls so far; racy increments are acceptable, it is a heuristic. */
    public long count;

    Entry(int slot) {
      this.slot = slot;
    }
  }

  private static final Entry OVERFLOW_ENTRY = new Entry(OVERFLOW);
  private static final AtomicInteger nextFirstCome = new AtomicInteger(0);
  private static int nextReserved = FIRST_COME;

  private static final ClassValue<Entry> ENTRIES = new ClassValue<Entry>() {
    @Override
    protected Entry computeValue(Class<?> c) {
      int s = nextFirstCome.getAndIncrement();
      return new Entry(s < FIRST_COME ? s : OVERFLOW);
    }
  };

  /** The entry for a lambda class; a constant overflow entry when the switch is off. */
  public static Entry entryOf(Class<?> c) {
    return RunLoopFlags.SYNC_SLOTS ? ENTRIES.get(c) : OVERFLOW_ENTRY;
  }

  /** Gives an overflow class a reserved slot, once, while any remain. */
  public static void promote(Entry e) {
    if (e == OVERFLOW_ENTRY || e.slot != OVERFLOW) return;
    synchronized (SyncSlots.class) {
      if (e.slot != OVERFLOW) return;
      if (nextReserved < SLOTS) {
        e.slot = nextReserved;
        nextReserved += 1;
      } else {
        e.count = Long.MIN_VALUE; // no slot left: never re-check
      }
    }
  }
}
