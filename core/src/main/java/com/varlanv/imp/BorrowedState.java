package com.varlanv.imp;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

final class BorrowedState {

    private final boolean isShared;
    private final ImpServerContext originalConfig;
    private final AtomicReference<ImpServerContext> mutableConfig = new AtomicReference<>();
    private final ReentrantLock lock = new ReentrantLock();

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
            try {
                lock();
                mutableConfig.set(config);
            } finally {
                unlock();
            }
            return supplier.get();
        } finally {
            mutableConfig.set(originalConfig);
        }
    }

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }
}
