package com.varlanv.imp;

final class Volatile<T> {

    private volatile T value;

    Volatile(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}
