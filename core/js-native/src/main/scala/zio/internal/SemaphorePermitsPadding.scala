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

package zio.internal

import java.util.concurrent.atomic.AtomicLong

/**
 * The permit counter of [[SemaphorePlatform]].
 *
 * On the JVM this is a padded field that keeps `permits` on a cache line of its
 * own. Neither Scala.js nor Scala Native can compile that Java class, and the
 * layout it controls is a JVM concern, so here it is a plain counter behind the
 * same interface. Keep the constructor and the three methods in step with the
 * JVM class in `core/jvm/src/main/java`; `SemaphorePlatform` is shared code and
 * compiles against both.
 */
private[zio] abstract class SemaphorePermitsPadding(initialPermits: Long) extends Serializable {
  private[this] val permits = new AtomicLong(initialPermits)

  protected final def getPermits(): Long = permits.get()

  protected final def compareAndSetPermits(expected: Long, updated: Long): Boolean =
    permits.compareAndSet(expected, updated)

  protected final def getAndAddPermits(delta: Long): Long = permits.getAndAdd(delta)
}
