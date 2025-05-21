package com.varlanv.imp;

public final class ImpStatistics {

    private final int hitCount;
    private final int missCount;

    public ImpStatistics(int hitCount, int missCount) {
        this.hitCount = hitCount;
        this.missCount = missCount;
    }

    ImpStatistics(MutableImpStatistics mutableStatistics) {
        this(mutableStatistics.hitCount(), mutableStatistics.missCount());
    }

    public int hitCount() {
        return hitCount;
    }

    public int missCount() {
        return missCount;
    }
}
