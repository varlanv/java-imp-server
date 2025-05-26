package com.varlanv.imp;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.intellij.lang.annotations.Language;

public final class ImpMatch {

    private static final ImpCondition EVERYTHING_INSTANCE = request -> true;

    ImpMatch() {}

    public ImpCondition everything() {
        return EVERYTHING_INSTANCE;
    }

    public ImpCondition and(ImpCondition... conditions) {
        Preconditions.nonNull(conditions, "conditions");
        var conditionList = List.of(conditions);
        Preconditions.notEmptyIterable(conditionList, "conditions");
        var newCondition = conditionList.get(0);
        for (var i = 1; i < conditionList.size(); i++) {
            newCondition = and(conditionList.get(i), newCondition);
        }
        return newCondition;
    }

    ImpCondition and(ImpCondition first, ImpCondition other) {
        return request -> first.test(request) && other.test(request);
    }

    public ImpCondition or(ImpCondition... conditions) {
        Preconditions.nonNull(conditions, "conditions");
        var conditionList = List.of(conditions);
        Preconditions.notEmptyIterable(conditionList, "conditions");
        var newCondition = conditionList.get(0);
        for (var i = 1; i < conditionList.size(); i++) {
            newCondition = or(conditionList.get(i), or(newCondition));
        }
        return newCondition;
    }

    ImpCondition or(ImpCondition first, ImpCondition other) {
        return request -> first.test(request) || other.test(request);
    }

    public Headers headers() {
        return new Headers();
    }

    public Body body() {
        return new Body();
    }

    public Url url() {
        return new Url();
    }

    public JsonPathMatch jsonPath(@Language("jsonpath") String jsonPath) {
        Preconditions.nonBlank(jsonPath, "jsonpath");
        return JsonPathInternal.forJsonPath(jsonPath);
    }

    public static final class Headers {

        Headers() {}

        public ImpCondition containsAllKeys(Set<String> expectedHeadersKeys) {
            Preconditions.noNullsInIterable(expectedHeadersKeys, "expectedHeadersKeys");
            Preconditions.notEmptyIterable(expectedHeadersKeys, "expectedHeadersKeys");
            return request -> {
                var headers = request.headers();
                for (var expectedKey : expectedHeadersKeys) {
                    if (!headers.containsKey(expectedKey)) {
                        return false;
                    }
                }
                return true;
            };
        }

        public ImpCondition containsKey(String expectedKey) {
            Preconditions.nonBlank(expectedKey, "expectedKey");
            return request -> request.headers().containsKey(expectedKey);
        }

        public ImpCondition containsValue(String expectedValue) {
            Preconditions.nonBlank(expectedValue, "expectedValue");
            return request -> {
                for (var valuesList : request.headers().values()) {
                    for (var value : valuesList) {
                        if (value.equals(expectedValue)) {
                            return true;
                        }
                    }
                }
                return false;
            };
        }

        public ImpCondition containsPair(String expectedKey, String expectedValue) {
            Preconditions.nonBlank(expectedKey, "expectedKey");
            Preconditions.nonBlank(expectedValue, "expectedValue");
            return request -> {
                var valuesList = request.headers().get(expectedKey);
                if (valuesList != null) {
                    for (var value : valuesList) {
                        if (value.equals(expectedValue)) {
                            return true;
                        }
                    }
                }
                return false;
            };
        }

        public ImpCondition containsPairList(String expectedKey, List<String> expectedValueList) {
            Preconditions.nonBlank(expectedKey, "expectedKey");
            Preconditions.nonBlank(expectedKey, "expectedValueList");
            return request -> {
                var valuesList = request.headers().get(expectedKey);
                return valuesList != null && valuesList.equals(expectedValueList);
            };
        }

        public ImpCondition hasContentType(String expectedContentType) {
            Preconditions.nonBlank(expectedContentType, "expectedContentType");
            return request -> {
                var contentTypeList = request.headers().get("Content-Type");
                if (contentTypeList != null) {
                    for (var value : contentTypeList) {
                        if (value.equals(expectedContentType)) {
                            return true;
                        }
                    }
                }
                return false;
            };
        }
    }

    public static final class Body {

        Body() {}

        public ImpCondition bodyContains(String substring) {
            Preconditions.nonNull(substring, "substring");
            return request -> request.body().contains(substring);
        }

        public ImpCondition bodyMatches(@Language("regexp") String pattern) {
            Preconditions.nonNull(pattern, "pattern");
            var compiledPattern = Pattern.compile(pattern);
            return request -> compiledPattern.matcher(request.body()).matches();
        }

        public ImpCondition bodyContainsIgnoreCase(String substring) {
            Preconditions.nonNull(substring, "substring");
            return request -> request.body().toLowerCase().contains(substring.toLowerCase());
        }

        public ImpCondition testBodyString(ImpPredicate<String> predicate) {
            Preconditions.nonNull(predicate, "predicate");
            return request -> predicate.test(request.body());
        }
    }

    public static final class Url {

        public ImpCondition urlMatches(@Language("regexp") String pattern) {
            Preconditions.nonNull(pattern, "pattern");
            var compiledPattern = Pattern.compile(pattern);
            return request -> compiledPattern.matcher(request.uri().uriString()).matches();
        }

        public ImpCondition urlContains(String substring) {
            Preconditions.nonNull(substring, "substring");
            return request -> request.uri().uriString().contains(substring);
        }

        public ImpCondition urlContainsIgnoreCase(String substring) {
            Preconditions.nonNull(substring, "substring");
            return request -> request.uri().uriString().toLowerCase().contains(substring.toLowerCase());
        }

        public ImpCondition hasQueryParamKey(String key) {
            Preconditions.nonNull(key, "key");
            return request -> request.uri().query().containsKey(key);
        }

        public ImpCondition hasQueryParam(String key, String value) {
            Preconditions.nonBlank(key, "key");
            Preconditions.nonNull(value, "value");
            return request -> Objects.equals(request.uri().query().get(key), value);
        }
    }
}
