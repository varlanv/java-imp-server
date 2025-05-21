package com.varlanv.imp;

import java.util.Map;
import org.jspecify.annotations.Nullable;

interface Preconditions {

    @SuppressWarnings("ConstantValue")
    static <K, V, M extends Map<K, V>> M noNullsInMap(M map, String fieldName) {
        Preconditions.nonNull(map, fieldName);
        for (var entry : map.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException(String.format(
                        "null key are not supported in %s, but found null key in entry [ %s ]", fieldName, entry));
            } else if (entry.getValue() == null) {
                throw new IllegalArgumentException(String.format(
                        "null values are not supported in %s, but found null values in entry [ %s ]",
                        fieldName, entry));
            }
        }
        return map;
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
