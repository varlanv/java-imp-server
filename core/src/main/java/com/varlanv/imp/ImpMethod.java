package com.varlanv.imp;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

enum ImpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS,
    TRACE;

    private static final Map<String, ImpMethod> cache;

    static {
        var values = values();
        var cacheTmp = new HashMap<String, ImpMethod>(values.length);
        for (var value : values) {
            cacheTmp.put(value.name(), value);
        }
        cache = cacheTmp;
    }

    @Nullable static ImpMethod of(String stringValue) {
        return cache.get(stringValue);
    }
}
