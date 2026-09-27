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

/**
 * Switches for {@code FiberRuntime.runLoop}, decided once at class
 * initialization (zio/zio#11251). They are {@code static final} fields of a
 * Java class so that HotSpot's C2 folds them and removes the untaken branch.
 */
public final class RunLoopFlags {

  private RunLoopFlags() {}

  /** System property that overrides {@link #SYNC_SLOTS}: "true" or "false". */
  public static final String SYNC_SLOTS_PROPERTY = "zio.runLoop.syncSlots";

  /**
   * Whether {@code runLoop} dispatches each {@code Sync} thunk and each
   * {@code FlatMap} continuation through a call site chosen by the lambda's
   * class (see {@link SyncSlots}), so that C2 sees a monomorphic site per
   * lambda class and inlines it regardless of what else ran at startup. On
   * x86 that inline is worth about 20 percent on tight loops; on Arm cores it
   * is the slow shape, so the default is on except for aarch64.
   */
  public static final boolean SYNC_SLOTS = computeSyncSlots();

  private static boolean computeSyncSlots() {
    try {
      String prop = System.getProperty(SYNC_SLOTS_PROPERTY);
      if (prop != null) {
        String v = prop.trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
      }
      String arch = System.getProperty("os.arch", "");
      return !(arch.equals("aarch64") || arch.equals("arm64"));
    } catch (Throwable t) {
      return false;
    }
  }
}
