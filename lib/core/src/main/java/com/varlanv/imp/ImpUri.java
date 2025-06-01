package com.varlanv.imp;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Unmodifiable;

public final class ImpUri {

    private final String urlString;
    private final MemoizedSupplier<Map<String, String>> querySupplier;

    ImpUri(URI requestedUri) {
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

    public String uriString() {
        return urlString;
    }

    @Unmodifiable
    public Map<String, String> query() {
        return querySupplier.get();
    }
}
