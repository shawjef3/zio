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

/**
 * The free-permit counter of a {@code zio.Semaphore}, kept on a cache line of
 * its own.
 *
 * The counter is the one word every acquire and release writes, so a contended
 * semaphore is bounded by how often that line bounces between cores. Whatever
 * else shares the line, the waiter queue's pointers or the waiting flag, is
 * invalidated along with it, and once the semaphore is promoted to the old
 * generation that layout is frozen for the life of the JVM. Padding on both
 * sides keeps the counter alone on its line and on the adjacent line that the
 * hardware prefetcher pairs it with, following {@link MutableQueueFieldsPadding}.
 */
public abstract class SemaphorePermits extends SemaphorePermitsPadding1 implements Serializable {
    private static final AtomicLongFieldUpdater<SemaphorePermitsValue> updater =
        AtomicLongFieldUpdater.newUpdater(SemaphorePermitsValue.class, "permitsValue");

    protected SemaphorePermits(long initial) {
        this.permitsValue = initial;
    }

    protected final long permitsGet() {
        return permitsValue;
    }

    protected final boolean permitsCompareAndSet(long expected, long updated) {
        return updater.compareAndSet(this, expected, updated);
    }

    protected final long permitsAddAndGet(long delta) {
        return updater.addAndGet(this, delta);
    }
}

abstract class SemaphorePermitsPadding0 implements Serializable {
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

abstract class SemaphorePermitsValue extends SemaphorePermitsPadding0 implements Serializable {
    protected volatile long permitsValue;
}

abstract class SemaphorePermitsPadding1 extends SemaphorePermitsValue implements Serializable {
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
}
