package com.varlanv.imp;

import org.jspecify.annotations.Nullable;

interface Preconditions {

    static <T> T nonNull(@Nullable T t, String field) {
        checkNonNull(t, field);
        return t;
    }

    static String nonBlank(@Nullable String t, String field) {
        if (t == null || t.isBlank()) {
            throw new IllegalArgumentException("null or blank strings are not supported - " + field);
        }
        return t;
    }

    private static void checkNonNull(@Nullable Object t, String field) {
        if (t == null) {
            throw new IllegalArgumentException("nulls are not supported - " + field);
        }
    }
}
