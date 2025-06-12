package com.varlanv.imp;

import java.util.concurrent.atomic.AtomicInteger;

final class BorrowedState {

    private final boolean isShared;
    private final ImpServerContext originalConfig;
    private final AtomicInteger inProgressRequestCounter;
    private final Volatile<ImpServerContext> mutableConfig;

    BorrowedState(ImpServerContext config, boolean isShared) {
        this.isShared = isShared;
        this.originalConfig = config;
        this.inProgressRequestCounter = new AtomicInteger();
        this.mutableConfig = new Volatile<>(config);
    }

    boolean isShared() {
        return isShared;
    }

    ImpServerContext currentContext() {
        return mutableConfig.get();
    }

    <T> T doWithLockedContext(ImpServerContext config, ImpSupplier<T> supplier) {
        try {
            mutableConfig.set(config);
            return supplier.get();
        } finally {
            mutableConfig.set(originalConfig);
        }
    }

    AtomicInteger inProgressRequestCounter() {
        return inProgressRequestCounter;
    }
}
