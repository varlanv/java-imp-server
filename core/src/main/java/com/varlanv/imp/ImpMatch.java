package com.varlanv.imp;

import java.util.*;
import java.util.regex.Pattern;
import org.intellij.lang.annotations.Language;

public final class ImpMatch {

    static final ImpCondition EVERYTHING_INSTANCE = new ImpCondition(
            ImpCondition.DEFAULT_GROUP, request -> true, () -> "*Everything* matcher", ImpCondition.Kind.CONDITION);

    ImpMatch() {}

    public ImpCondition everything() {
        return EVERYTHING_INSTANCE;
    }

    public ImpCondition and(ImpCondition... conditions) {
        Preconditions.nonNull(conditions, "conditions");
        var conditionList = Arrays.asList(conditions);
        Preconditions.notEmptyIterable(conditionList, "conditions");
        Preconditions.noNullsInIterable(conditionList, "conditions");
        return new ImpCondition(
                ImpCondition.DEFAULT_GROUP,
                EVERYTHING_INSTANCE.predicate,
                () -> "",
                ImpCondition.Kind.AND,
                List.copyOf(conditionList));
    }

    public ImpCondition not(ImpCondition condition) {
        Preconditions.nonNull(condition, "condition");
        return new ImpCondition(
                condition.group,
                condition.predicate,
                () -> "not " + condition.context.get(),
                ImpCondition.Kind.NOT,
                condition.nested);
    }

    public ImpCondition or(ImpCondition... conditions) {
        Preconditions.nonNull(conditions, "conditions");
        var conditionList = Arrays.asList(conditions);
        Preconditions.notEmptyIterable(conditionList, "conditions");
        Preconditions.noNullsInIterable(conditionList, "conditions");
        return new ImpCondition(
                ImpCondition.DEFAULT_GROUP,
                EVERYTHING_INSTANCE.predicate,
                () -> "",
                ImpCondition.Kind.OR,
                List.copyOf(conditionList));
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

        private static final String GROUP = "Headers";

        Headers() {}

        public ImpCondition containsAllKeys(Set<String> expectedHeadersKeys) {
            Preconditions.noNullsInIterable(expectedHeadersKeys, "expectedHeadersKeys");
            Preconditions.notEmptyIterable(expectedHeadersKeys, "expectedHeadersKeys");
            var expectedHeadersKeysCopy = new LinkedHashSet<>(expectedHeadersKeys);
            return new ImpCondition(
                    GROUP,
                    request -> {
                        var headers = request.headers();
                        for (var expectedKey : expectedHeadersKeysCopy) {
                            if (!headers.containsKey(expectedKey)) {
                                return false;
                            }
                        }
                        return true;
                    },
                    () -> String.format("containsAllKeys(\"%s\")", expectedHeadersKeysCopy),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition containsKey(String expectedKey) {
            Preconditions.nonBlank(expectedKey, "expectedKey");
            return new ImpCondition(
                    GROUP,
                    request -> request.headers().containsKey(expectedKey),
                    () -> String.format("containsKey(\"%s\")", expectedKey),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition containsValue(String expectedValue) {
            Preconditions.nonBlank(expectedValue, "expectedValue");
            return new ImpCondition(
                    GROUP,
                    request -> {
                        for (var valuesList : request.headers().values()) {
                            for (var value : valuesList) {
                                if (value.equals(expectedValue)) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    },
                    () -> String.format("containsValue(%s)", expectedValue),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition containsPair(String expectedKey, String expectedValue) {
            Preconditions.nonBlank(expectedKey, "expectedKey");
            Preconditions.nonBlank(expectedValue, "expectedValue");
            return new ImpCondition(
                    GROUP,
                    request -> {
                        var valuesList = request.headers().get(expectedKey);
                        if (valuesList != null) {
                            for (var value : valuesList) {
                                if (value.equals(expectedValue)) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    },
                    () -> String.format("containsPair(%s, %s)", expectedKey, expectedKey),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition containsPairList(String expectedKey, List<String> expectedValueList) {
            Preconditions.nonBlank(expectedKey, "expectedKey");
            Preconditions.nonBlank(expectedKey, "expectedValueList");
            var expectedValueListCopy = List.copyOf(expectedValueList);
            return new ImpCondition(
                    GROUP,
                    request -> {
                        var valuesList = request.headers().get(expectedKey);
                        return valuesList != null && valuesList.equals(expectedValueListCopy);
                    },
                    () -> String.format("containsPairList(%s, %s)", expectedKey, expectedValueListCopy),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition hasContentType(String expectedContentType) {
            Preconditions.nonBlank(expectedContentType, "expectedContentType");
            return new ImpCondition(
                    GROUP,
                    request -> {
                        var contentTypeList = request.headers().get("Content-Type");
                        if (contentTypeList != null) {
                            for (var value : contentTypeList) {
                                if (value.equals(expectedContentType)) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    },
                    () -> String.format("hasContentType(%s)", expectedContentType),
                    ImpCondition.Kind.CONDITION);
        }
    }

    public static final class Body {

        private static final String GROUP = "Body";

        Body() {}

        public ImpCondition bodyContains(String substring) {
            Preconditions.nonNull(substring, "substring");
            return new ImpCondition(
                    GROUP,
                    request -> request.body().contains(substring),
                    () -> String.format("bodyContains(%s)", substring),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition bodyMatches(@Language("regexp") String pattern) {
            Preconditions.nonNull(pattern, "pattern");
            var compiledPattern = Pattern.compile(pattern);
            return new ImpCondition(
                    GROUP,
                    request -> compiledPattern.matcher(request.body()).matches(),
                    () -> String.format("bodyMatches(%s)", pattern),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition bodyContainsIgnoreCase(String substring) {
            Preconditions.nonNull(substring, "substring");
            return new ImpCondition(
                    GROUP,
                    request -> request.body().toLowerCase().contains(substring.toLowerCase()),
                    () -> String.format("bodyContainsIgnoreCase(%s)", substring),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition testBodyString(ImpPredicate<String> predicate) {
            Preconditions.nonNull(predicate, "predicate");
            return new ImpCondition(
                    GROUP,
                    request -> predicate.test(request.body()),
                    () -> "testBodyString(<predicate>)",
                    ImpCondition.Kind.CONDITION);
        }
    }

    public static final class Url {

        private static final String GROUP = "Url";

        Url() {}

        public ImpCondition urlMatches(@Language("regexp") String pattern) {
            Preconditions.nonNull(pattern, "pattern");
            var compiledPattern = Pattern.compile(pattern);
            return new ImpCondition(
                    GROUP,
                    request ->
                            compiledPattern.matcher(request.uri().uriString()).matches(),
                    () -> String.format("urlMatches(%s)", pattern),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition urlContains(String substring) {
            Preconditions.nonNull(substring, "substring");
            return new ImpCondition(
                    GROUP,
                    request -> request.uri().uriString().contains(substring),
                    () -> String.format("urlContains(%s)", substring),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition urlContainsIgnoreCase(String substring) {
            Preconditions.nonNull(substring, "substring");
            return new ImpCondition(
                    GROUP,
                    request -> request.uri().uriString().toLowerCase().contains(substring.toLowerCase()),
                    () -> String.format("urlContainsIgnoreCase(%s)", substring),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition hasQueryParamKey(String key) {
            Preconditions.nonNull(key, "key");
            return new ImpCondition(
                    GROUP,
                    request -> request.uri().query().containsKey(key),
                    () -> String.format("hasQueryParamKey(%s)", key),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition hasQueryParam(String key, String value) {
            Preconditions.nonBlank(key, "key");
            Preconditions.nonNull(value, "value");
            return new ImpCondition(
                    GROUP,
                    request -> Objects.equals(request.uri().query().get(key), value),
                    () -> String.format("hasQueryParam(%s)", key),
                    ImpCondition.Kind.CONDITION);
        }
    }
}
