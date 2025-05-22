package com.varlanv.imp;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class BorrowedState {

    private final boolean isShared;
    private final ImpServerContext originalConfig;
    private final AtomicInteger inProgressRequestCounter = new AtomicInteger();
    private final AtomicReference<ImpServerContext> mutableConfig = new AtomicReference<>();

    BorrowedState(ImpServerContext config, boolean isShared) {
        this.originalConfig = config;
        this.mutableConfig.set(config);
        this.isShared = isShared;
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
