package com.varlanv.imp;

import java.util.concurrent.atomic.AtomicInteger;

public final class ImpStatistics {

    private final AtomicInteger hitCount = new AtomicInteger();
    private final AtomicInteger missCount = new AtomicInteger();

    public int hitCount() {
        return hitCount.get();
    }

    public int missCount() {
        return missCount.get();
    }

    void incrementHitCount() {
        hitCount.incrementAndGet();
    }

    void incrementMissCount() {
        missCount.incrementAndGet();
    }
}
