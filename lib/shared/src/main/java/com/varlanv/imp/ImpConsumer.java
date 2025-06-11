package com.varlanv.imp;

@FunctionalInterface
public interface ImpConsumer<T> {

    default void accept(T t) {
        try {
            unsafeAccept(t);
        } catch (Exception e) {
            InternalUtils.hide(e);
        }
    }

    void unsafeAccept(T t) throws Exception;
}
