package com.varlanv.imp;

import java.util.function.Predicate;

@FunctionalInterface
public interface ImpPredicate<T> extends Predicate<T> {

    static <T> ImpPredicate<T> alwaysTrue() {
        return any -> true;
    }

    static <T> ImpPredicate<T> alwaysFalse() {
        return any -> false;
    }

    @Override
    default boolean test(T t) {
        try {
            return unsafeTest(t);
        } catch (Exception e) {
            return InternalUtils.hide(e);
        }
    }

    boolean unsafeTest(T t) throws Exception;
}
