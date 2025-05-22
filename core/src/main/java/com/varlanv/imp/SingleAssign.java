package com.varlanv.imp;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class SingleAssign<T> {

    private final Supplier<T> fallback;
    private final String fieldName;

    @Nullable private T value;

    SingleAssign(T fallback, String fieldName) {
        this(() -> fallback, fieldName);
    }

    SingleAssign(Supplier<T> fallback, String fieldName) {
        this.fallback = fallback;
        this.fieldName = fieldName;
    }

    void set(T value) {
        if (this.value != null) {
            throw new IllegalStateException("Attempting to reassign '" + fieldName + "'. Assign operations for '"
                    + fieldName + "' are not additive and should be done only once.");
        }
        this.value = value;
    }

    T get() {
        if (value == null) {
            return fallback.get();
        }
        return value;
    }
}
