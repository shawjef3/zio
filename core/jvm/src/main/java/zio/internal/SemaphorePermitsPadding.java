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

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/*
 * The permit counter of [[SemaphorePlatform]], on a cache line of its own.
 *
 * `permits` is written on every uncontended acquisition and release, from
 * whichever thread happens to be running the fiber, so it is the hottest
 * shared word in a semaphore. Held in a separate `AtomicLong` it lands
 * wherever the garbage collector copies it, and when a contended semaphore is
 * promoted to the old generation that placement is frozen for the rest of the
 * JVM's life. Measured with `SemaphoreContendedBenchmark` at 10 fibers and 5
 * permits, the JVMs in which `permits` shared a 128-byte line pair with the
 * waiter queue's `head` and `tail` were consistently the slowest, by up to a
 * third. Padding it fixes the placement. In one session of 12 JVMs each, the
 * mean went from 608 to 661 ops/s, the worst JVM from 492 to 594, and the
 * spread between JVMs from a CV of 10.0% to 3.8%, while the uncontended path
 * was unchanged (1918 against 1921 ops/s).
 *
 * This is written in Java for the same reason as
 * [[MutableQueueFieldsPadding]]: `AtomicLongFieldUpdater` needs a naked
 * volatile field, which Scala does not produce, and the padding relies on the
 * JVM laying out a superclass's fields before its subclass's, which only an
 * inheritance chain guarantees. There are 16 longs on each side, 128 bytes,
 * because x86 prefetches cache lines in adjacent pairs.
 *
 * Only `permits` is padded. Padding the waiter counts as well was measured and
 * bought nothing further.
 *
 * Keep in mind when changing this:
 *
 *   - The `pNNN` fields are never read. They exist only to take up space, and
 *     removing them, or tidying them into fewer fields, silently undoes the fix.
 *   - Add no other fields to these classes. Anything declared next to `permits`
 *     shares its cache line again.
 *   - Keep `permits` a field of this chain rather than a separate object: an
 *     `AtomicLong` (padded or not) is placed by the garbage collector, which is
 *     exactly what went wrong. Keep it out of `SemaphorePlatform` itself too,
 *     since the JVM orders a class's own fields by size and padding declared
 *     alongside them does not stay where it is written.
 *   - `core/js-native` has a Scala class of the same name standing in for this
 *     one. Its constructor and methods must match, or `SemaphorePlatform` stops
 *     compiling on those platforms.
 *   - A layout regression does not fail any test. Check it with
 *     `SemaphoreContendedBenchmark` at 5 permits and 12 forks: the spread
 *     between forks, not the mean, is what shows it.
 */
public abstract class SemaphorePermitsPadding extends SemaphorePermitsField implements Serializable {
    private static final AtomicLongFieldUpdater<SemaphorePermitsField> permitsUpdater =
        AtomicLongFieldUpdater.newUpdater(SemaphorePermitsField.class, "permits");

    protected long p100;
    protected long p101;
    protected long p102;
    protected long p103;
    protected long p104;
    protected long p105;
    protected long p106;
    protected long p107;
    protected long p108;
    protected long p109;
    protected long p110;
    protected long p111;
    protected long p112;
    protected long p113;
    protected long p114;
    protected long p115;

    protected SemaphorePermitsPadding(long initialPermits) {
        permits = initialPermits;
    }

    protected final long getPermits() {
        return permits;
    }

    protected final boolean compareAndSetPermits(long expected, long updated) {
        return permitsUpdater.compareAndSet(this, expected, updated);
    }

    protected final long getAndAddPermits(long delta) {
        return permitsUpdater.getAndAdd(this, delta);
    }
}

// Aux classes below

abstract class SemaphorePermitsLeadingPadding implements Serializable {
    protected long p000;
    protected long p001;
    protected long p002;
    protected long p003;
    protected long p004;
    protected long p005;
    protected long p006;
    protected long p007;
    protected long p008;
    protected long p009;
    protected long p010;
    protected long p011;
    protected long p012;
    protected long p013;
    protected long p014;
    protected long p015;
}

abstract class SemaphorePermitsField extends SemaphorePermitsLeadingPadding implements Serializable {
    protected volatile long permits;
}
