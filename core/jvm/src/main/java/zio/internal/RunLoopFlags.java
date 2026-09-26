/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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
 * Build-time-style switches for {@code FiberRuntime.runLoopInner}, decided
 * once at class initialization (zio/zio#11251).
 *
 * <p>They are {@code static final} fields of a Java class on purpose: HotSpot's
 * C2 treats a {@code static final} primitive as a constant and removes the
 * branch that tests it, so the untaken path costs nothing at run time. A Scala
 * {@code object} member would be an instance field of the module class, which
 * C2 does not trust as a constant.
 */
public final class RunLoopFlags {

  private RunLoopFlags() {}

  /** System property that overrides {@link #USER_CODE_VIA_HELPERS}: "true" or "false". */
  public static final String USER_CODE_VIA_HELPERS_PROPERTY = "zio.runLoop.userCodeViaHelpers";

  /**
   * Whether {@code runLoopInner} invokes application lambdas (a {@code Sync}
   * thunk and the success continuations) through helper methods that C2
   * compiles separately, or calls them directly from the loop. "User code via
   * helpers" means the loop never invokes an application lambda itself, so C2
   * cannot inline one into the loop's compiled body.
   *
   * <p>Measured on five microarchitectures with the same JDK build
   * (Temurin 25.0.4): on Arm cores (Neoverse V1, Neoverse V2, Apple M2 Pro)
   * the helper form gains 10 to 26 percent on tight loops, because C2's
   * guarded inline of the lambda into the loop stalls those cores' back ends;
   * on x86 cores (Zen 5, Ice Lake) the same helpers lose 11 to 26 percent,
   * because there the inline saves about 30 percent of instructions with no
   * stall. Hence the default is on for aarch64 and off elsewhere. The property
   * {@link #USER_CODE_VIA_HELPERS_PROPERTY} overrides the default either way.
   */
  public static final boolean USER_CODE_VIA_HELPERS = computeUserCodeViaHelpers();

  private static boolean computeUserCodeViaHelpers() {
    try {
      String prop = System.getProperty(USER_CODE_VIA_HELPERS_PROPERTY);
      if (prop != null) {
        String v = prop.trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
      }
      String arch = System.getProperty("os.arch", "");
      return arch.equals("aarch64") || arch.equals("arm64");
    } catch (Throwable t) {
      return false;
    }
  }
}
