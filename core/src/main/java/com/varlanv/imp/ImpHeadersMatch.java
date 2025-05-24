package com.varlanv.imp;

import com.sun.net.httpserver.Headers;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ImpHeadersMatch {

    private final Map<String, List<String>> headers;

    @SuppressWarnings("PMD")
    ImpHeadersMatch(Headers headers) {
        this.headers = headers;
    }

    public boolean containsAllKeys(Set<String> expectedHeadersKeys) {
        Preconditions.noNullsInIterable(expectedHeadersKeys, "expectedHeadersKeys");
        for (var key : expectedHeadersKeys) {
            if (!headers.containsKey(key)) {
                return false;
            }
        }
        return true;
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
}
