package com.varlanv.imp;

interface NamedFn<T, R> extends ImpFn<T, R> {

    String name();

    static <T, R> NamedFn<T, R> from(String name, ImpFn<T, R> function) {
        return new NamedFn<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public R unsafeApply(T t) throws Exception {
                return function.apply(t);
            }
        };
    }
}
