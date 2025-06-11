package com.varlanv.imp;

@FunctionalInterface
public interface ImpSupplier<T> {

    default T get() {
        try {
            return unsafeGet();
        } catch (Exception e) {
            return InternalUtils.hide(e);
        }
    }

    T unsafeGet() throws Exception;
}
