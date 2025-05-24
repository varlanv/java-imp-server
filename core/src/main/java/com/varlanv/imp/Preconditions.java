package com.varlanv.imp;

import java.util.Map;
import org.jspecify.annotations.Nullable;

interface Preconditions {

    @SuppressWarnings("ConstantValue")
    static <K, V, I extends Iterable<V>, M extends Map<K, I>> M noNullsInHeaders(M map, String fieldName) {
        nonNull(map, fieldName);
        for (var entry : map.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException(String.format(
                        "null key are not supported in %s, but found null key in entry [ %s ]", fieldName, entry));
            } else {
                var value = entry.getValue();
                if (value == null) {
                    throw new IllegalArgumentException(String.format(
                            "null values are not supported in %s, but found null values in entry [ %s ]",
                            fieldName, entry));
                } else {
                    for (var v : value) {
                        if (v == null) {
                            throw new IllegalArgumentException(String.format(
                                    "null values are not supported in %s, but found null values in entry [ %s ]",
                                    fieldName, entry));
                        }
                    }
                }
            }
        }
        return map;
    }

    @SuppressWarnings("ConstantValue")
    static <T, I extends Iterable<T>> I noNullsInIterable(I iterable, String fieldName) {
        nonNull(iterable, fieldName);
        var counter = 0;
        for (var value : iterable) {
            if (value == null) {
                throw new IllegalArgumentException(String.format(
                        "null values are not supported in %s, but found null value at position [%d]",
                        fieldName, counter));
            }
            counter++;
        }
        return iterable;
    }

    static ImpHttpStatus validHttpStatusCode(int statusCode) {
        var httpStatus = ImpHttpStatus.forCodeNullable(statusCode);
        if (httpStatus == null) {
            throw new IllegalArgumentException(String.format("Invalid http status code [%d]", statusCode));
        }
        return httpStatus;
    }

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
