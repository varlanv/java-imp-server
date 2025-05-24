package com.varlanv.imp;

interface NamedSupplier<T> extends ImpSupplier<T> {

    String name();

    static <T> NamedSupplier<T> from(String name, ImpSupplier<T> supplier) {
        return new NamedSupplier<T>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public T unsafeGet() throws Exception {
                return supplier.get();
            }
        };
    }
}
