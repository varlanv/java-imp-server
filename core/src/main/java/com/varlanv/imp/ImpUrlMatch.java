package com.varlanv.imp;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.intellij.lang.annotations.Language;

public final class ImpUrlMatch {

    private final String urlString;
    private final MemoizedSupplier<Map<String, String>> querySupplier;

    ImpUrlMatch(URI requestedUri) {
        this.urlString = requestedUri.toString();
        this.querySupplier = MemoizedSupplier.of(() -> {
            var query = Objects.requireNonNullElse(requestedUri.getQuery(), "");
            if (query.isEmpty()) {
                return Map.of();
            }
            var queryMap = new HashMap<String, String>();
            var split = query.split("&");
            for (var queryEntry : split) {
                var queryKeyVal = queryEntry.split("=", 2);
                if (queryKeyVal.length == 2) {
                    queryMap.put(queryKeyVal[0], queryKeyVal[1]);
                } else {
                    queryMap.put(queryKeyVal[0], "");
                }
            }
            return Collections.unmodifiableMap(queryMap);
        });
    }

    public boolean urlMatches(@Language("regexp") String pattern) {
        Preconditions.nonNull(pattern, "pattern");
        return urlString.matches(pattern);
    }

    public boolean urlContains(String substring) {
        Preconditions.nonNull(substring, "substring");
        return urlString.contains(substring);
    }

    public boolean urlContainsIgnoreCase(String substring) {
        Preconditions.nonNull(substring, "substring");
        return urlString.toLowerCase().contains(substring.toLowerCase());
    }

    public boolean hasQueryParamKey(String key) {
        Preconditions.nonNull(key, "key");
        return querySupplier.get().containsKey(key);
    }

    public boolean hasQueryParam(String key, String value) {
        Preconditions.nonNull(key, "key");
        Preconditions.nonNull(value, "value");
        return Objects.equals(querySupplier.get().get(key), value);
    }
}
