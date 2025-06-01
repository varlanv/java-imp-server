package com.varlanv.imp;

import java.util.function.Supplier;

@FunctionalInterface
public interface ImpSupplier<T> extends Supplier<T> {

    @Override
    default T get() {
        try {
            return unsafeGet();
        } catch (Exception e) {
            return InternalUtils.hide(e);
        }
    }

    T unsafeGet() throws Exception;
}
