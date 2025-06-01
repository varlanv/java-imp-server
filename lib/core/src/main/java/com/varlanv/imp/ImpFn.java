package com.varlanv.imp;

import java.util.function.Function;

@FunctionalInterface
public interface ImpFn<T, R> extends Function<T, R> {

    @Override
    default R apply(T t) {
        try {
            return unsafeApply(t);
        } catch (Exception e) {
            return InternalUtils.hide(e);
        }
    }

    R unsafeApply(T t) throws Exception;
}
