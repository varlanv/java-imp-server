package com.varlanv.imp;

interface PortSupplier {

    int value();

    boolean isRandom();

    static PortSupplier ofSupplier(ImpSupplier<Integer> supplier, boolean isRandom) {
        return new PortSupplier() {

            @Override
            public boolean isRandom() {
                return isRandom;
            }

            @Override
            public int value() {
                return supplier.get();
            }
        };
    }

    static PortSupplier fixed(int port) {
        if (port < 0) {
            throw new IllegalArgumentException("Port value should be greater than 0. Received " + port);
        }
        if (port == 0) {
            throw new IllegalArgumentException(
                    "To use server on random port, use onRandomPort() or startSharedOnRandomPort() methods instead of fixed 0 value");
        }
        return new PortSupplier() {

            @Override
            public boolean isRandom() {
                return false;
            }

            @Override
            public int value() {
                return port;
            }
        };
    }
}
