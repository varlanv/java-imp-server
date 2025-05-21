package com.varlanv.imp;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Unmodifiable;

public final class ImpHeadersMatch {

    private final Map<String, List<String>> headers;

    public ImpHeadersMatch(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public boolean containsKey(String expectedKey) {
        Preconditions.nonBlank(expectedKey, "expectedKey");
        return headers.containsKey(expectedKey);
    }

    public boolean containsValue(String expectedValue) {
        Preconditions.nonBlank(expectedValue, "expectedValue");
        for (var valuesList : headers.values()) {
            for (var value : valuesList) {
                if (value.equals(expectedValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean containsPair(String expectedKey, String expectedValue) {
        Preconditions.nonBlank(expectedKey, "expectedKey");
        Preconditions.nonBlank(expectedValue, "expectedValue");
        var valuesList = headers.get(expectedKey);
        if (valuesList != null) {
            for (var value : valuesList) {
                if (value.equals(expectedValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean containsPairList(String expectedKey, List<String> expectedValueList) {
        Preconditions.nonBlank(expectedKey, "expectedKey");
        Preconditions.nonBlank(expectedKey, "expectedValueList");
        var valuesList = headers.get(expectedKey);
        return valuesList != null && valuesList.equals(expectedValueList);
    }

    public boolean hasContentType(String expectedContentType) {
        Preconditions.nonBlank(expectedContentType, "expectedContentType");
        var contentTypeList = headers.get("Content-Type");
        if (contentTypeList != null) {
            for (var value : contentTypeList) {
                if (value.equals(expectedContentType)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unmodifiable
    public Map<String, List<String>> headers() {
        var newMap = new HashMap<String, List<String>>(headers.size());
        headers.forEach((k, v) -> newMap.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(newMap);
    }
}
