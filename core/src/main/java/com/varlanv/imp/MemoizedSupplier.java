package com.varlanv.imp;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

final class MemoizedSupplier<T> {

    private final ImpSupplier<T> delegate;

    @Nullable private T value;

    private final Lock lock = new ReentrantLock();

    private MemoizedSupplier(ImpSupplier<T> delegate) {
        this.delegate = delegate;
    }

    static <T> MemoizedSupplier<T> of(ImpSupplier<T> delegate) {
        return new MemoizedSupplier<>(delegate);
    }

    T get() {
        T val = value;
        if (val == null) {
            try {
                lock.lock();
                val = value;
                if (val == null) {
                    val = delegate.get();
                    //noinspection ConstantValue
                    if (val == null) {
                        throw new IllegalStateException("Supplier returned null");
                    }
                    value = val;
                }
            } catch (Exception e) {
                return InternalUtils.hide(e);
            } finally {
                lock.unlock();
            }
        }
        return val;
    }

    public boolean isInitialized() {
        return value != null;
    }
}
