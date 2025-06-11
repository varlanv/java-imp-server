package com.varlanv.imp;

@FunctionalInterface
public interface ImpFn<T, R> {

    default R apply(T t) {
        try {
            return unsafeApply(t);
        } catch (Exception e) {
            return InternalUtils.hide(e);
        }
    }

    R unsafeApply(T t) throws Exception;
}
