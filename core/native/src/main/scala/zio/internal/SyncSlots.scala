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

/** Counterpart of the JVM `SyncSlots`; every class shares the overflow entry. */
private[zio] object SyncSlots {
  final val SLOTS         = 16
  final val OVERFLOW      = SLOTS
  final val PROMOTE_AFTER = 1L << 14

  final class Entry(val slot: Int) {
    var count: Long = 0L
  }

  private[this] val overflowEntry     = new Entry(OVERFLOW)
  def entryOf(c: Class[_]): Entry     = overflowEntry
  def promote(e: Entry): Unit         = ()
}
