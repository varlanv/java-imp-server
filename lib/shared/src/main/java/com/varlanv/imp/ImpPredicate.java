package com.varlanv.imp;

@FunctionalInterface
public interface ImpPredicate<T> {

    static <T> ImpPredicate<T> alwaysTrue() {
        return any -> true;
    }

    static <T> ImpPredicate<T> alwaysFalse() {
        return any -> false;
    }

    default boolean test(T t) {
        try {
            return unsafeTest(t);
        } catch (Exception e) {
            return InternalUtils.hide(e);
        }
    }

    boolean unsafeTest(T t) throws Exception;
}
