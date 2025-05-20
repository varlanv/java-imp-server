package com.varlanv.imp;

import java.util.function.Consumer;

@FunctionalInterface
public interface ImpConsumer<T> extends Consumer<T> {

    default void accept(T t) {
        try {
            unsafeAccept(t);
        } catch (Exception e) {
            InternalUtils.hide(e);
        }
    }

    void unsafeAccept(T t) throws Exception;
}
