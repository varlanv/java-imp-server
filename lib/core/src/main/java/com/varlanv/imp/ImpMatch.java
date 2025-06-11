package com.varlanv.imp;

import java.util.*;
import java.util.regex.Pattern;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;

public final class ImpMatch {

    static final ImpCondition EVERYTHING_INSTANCE = new ImpCondition(
            ImpCondition.DEFAULT_GROUP, request -> true, () -> "*Everything* matcher", ImpCondition.Kind.ALWAYS_TRUE);

    ImpMatch() {}

    public ImpCondition everything() {
        return EVERYTHING_INSTANCE;
    }

    public ImpCondition and(ImpCondition... conditions) {
        var conditionList = verifiedConditions(conditions);
        return new ImpCondition(
                ImpCondition.DEFAULT_GROUP,
                EVERYTHING_INSTANCE.predicate,
                InternalUtils.emptyStringSupplier(),
                ImpCondition.Kind.AND,
                List.copyOf(conditionList));
    }

    public ImpCondition not(ImpCondition condition) {
        Preconditions.nonNull(condition, "condition");
        if (condition == EVERYTHING_INSTANCE) {
            throw new IllegalArgumentException("Negating *Everything* matcher is not allowed");
        } else if (condition.kind == ImpCondition.Kind.AND || condition.kind == ImpCondition.Kind.OR) {
            throw new IllegalArgumentException("Negating *And* or *Or* matcher is currently not supported");
        }
        return new ImpCondition(
                condition.group,
                request -> !condition.predicate.test(request),
                () -> "not " + condition.context.get(),
                ImpCondition.Kind.NOT,
                condition.nested);
    }

    public ImpCondition or(ImpCondition... conditions) {
        var conditionList = verifiedConditions(conditions);
        return new ImpCondition(
                ImpCondition.DEFAULT_GROUP,
                EVERYTHING_INSTANCE.predicate,
                InternalUtils.emptyStringSupplier(),
                ImpCondition.Kind.OR,
                List.copyOf(conditionList));
    }

    public Headers headers() {
        return new Headers();
    }

    public Method method() {
        return new Method();
    }

    public Body body() {
        return new Body();
    }

    public Path path() {
        return new Path();
    }

    public Query query() {
        return new Query();
    }

    public JsonPathMatch jsonPath(@Language("jsonpath") String jsonPath) {
        Preconditions.nonBlank(jsonPath, "jsonpath");
        return JsonPathInternal.forJsonPath(jsonPath);
    }

    public static final class Method {

        private static final String GROUP = "Method";

        Method() {}

        public ImpCondition is(
                @MagicConstant(stringValues = {"GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH"})
                        String expectedMethod) {
            Preconditions.nonBlank(expectedMethod, "expectedMethod");
            if (ImpMethod.of(expectedMethod) == null) {
                throw new IllegalArgumentException(String.format("Unknown HTTP method: \"%s\"", expectedMethod));
            }
            return new ImpCondition(
                    GROUP,
                    request -> request.method().equals(expectedMethod),
                    () -> String.format("is(\"%s\")", expectedMethod),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition anyOf(
                @MagicConstant(stringValues = {"GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH"})
                        String... expectedMethods) {
            Preconditions.nonNull(expectedMethods, "expectedMethods");
            var expectedMethodsSet = new LinkedHashSet<String>(expectedMethods.length);
            for (int idx = 0, expectedMethodsLength = expectedMethods.length; idx < expectedMethodsLength; idx++) {
                var method = expectedMethods[idx];
                //noinspection ConstantValue
                if (method == null) {
                    throw new IllegalArgumentException(String.format(
                            "Nulls are not supported in expectedMethods, but found null at position [%d]", idx));
                }
                if (ImpMethod.of(method) == null) {
                    throw new IllegalArgumentException(String.format("Unknown HTTP method: \"%s\"", method));
                }
                if (!expectedMethodsSet.add(method)) {
                    throw new IllegalArgumentException(
                            String.format("Found duplicate value [%s], please check your configuration.", method));
                }
            }
            if (expectedMethodsSet.isEmpty()) {
                throw new IllegalArgumentException(
                        "No values were provided in expectedMethods, please check your configuration.");
            }
            return new ImpCondition(
                    GROUP,
                    request -> expectedMethodsSet.contains(request.method()),
                    () -> String.format("anyOf(\"%s\")", expectedMethodsSet),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition get() {
            return isInternal(ImpMethod.GET);
        }

        public ImpCondition post() {
            return isInternal(ImpMethod.POST);
        }

        public ImpCondition put() {
            return isInternal(ImpMethod.PUT);
        }

        public ImpCondition delete() {
            return isInternal(ImpMethod.DELETE);
        }

        public ImpCondition patch() {
            return isInternal(ImpMethod.PATCH);
        }

        public ImpCondition head() {
            return isInternal(ImpMethod.HEAD);
        }

        public ImpCondition options() {
            return isInternal(ImpMethod.OPTIONS);
        }

        public ImpCondition trace() {
            return isInternal(ImpMethod.TRACE);
        }

        private ImpCondition isInternal(ImpMethod expectedMethod) {
            return new ImpCondition(
                    GROUP,
                    request -> request.method().equals(expectedMethod.name()),
                    () -> expectedMethod.name().toLowerCase(Locale.ROOT) + "()",
                    ImpCondition.Kind.CONDITION);
        }
    }

    public static final class Headers {

        private static final String GROUP = "Headers";

        Headers() {}

        public ImpCondition containsAllKeys(Iterable<String> expectedHeadersKeys) {
            Preconditions.noNullsInIterable(expectedHeadersKeys, "expectedHeadersKeys");
            Preconditions.notEmptyIterable(expectedHeadersKeys, "expectedHeadersKeys");
            var expectedHeadersKeysCopy = new LinkedHashSet<String>();
            expectedHeadersKeys.forEach(expectedHeadersKeysCopy::add);
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
                    () -> String.format("containsValue(\"%s\")", expectedValue),
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
                    () -> String.format("containsPair(\"%s\", \"%s\")", expectedKey, expectedKey),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition containsPairList(String expectedKey, List<String> expectedValueList) {
            Preconditions.nonBlank(expectedKey, "expectedKey");
            Preconditions.noNullsInIterable(expectedValueList, "expectedValueList");
            var expectedValueListCopy = List.copyOf(expectedValueList);
            return new ImpCondition(
                    GROUP,
                    request -> {
                        var valuesList = request.headers().get(expectedKey);
                        return valuesList != null && valuesList.equals(expectedValueListCopy);
                    },
                    () -> String.format("containsPairList(\"%s, \"%s\")", expectedKey, expectedValueListCopy),
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
                    () -> String.format("hasContentType(\"%s\")", expectedContentType),
                    ImpCondition.Kind.CONDITION);
        }
    }

    public static final class Body {

        private static final String GROUP = "Body";

        Body() {}

        public ImpCondition contains(String substring) {
            Preconditions.nonBlank(substring, "substring");
            return new ImpCondition(
                    GROUP,
                    request -> request.body().contains(substring),
                    () -> String.format("contains(\"%s\")", substring),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition matches(@Language("regexp") String pattern) {
            Preconditions.nonBlank(pattern, "pattern");
            var compiledPattern = Pattern.compile(pattern);
            return new ImpCondition(
                    GROUP,
                    request -> compiledPattern.matcher(request.body()).matches(),
                    () -> String.format("matches(\"%s\")", pattern),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition containsIgnoreCase(String substring) {
            Preconditions.nonBlank(substring, "substring");
            return new ImpCondition(
                    GROUP,
                    request -> request.body().toLowerCase(Locale.ROOT).contains(substring.toLowerCase(Locale.ROOT)),
                    () -> String.format("containsIgnoreCase(\"%s\")", substring),
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

    public static final class Query {

        private static final String GROUP = "Query";

        Query() {}

        public ImpCondition hasKey(String key) {
            Preconditions.nonBlank(key, "key");
            return new ImpCondition(
                    GROUP,
                    request -> request.uri().query().containsKey(key),
                    () -> String.format("hasKey(\"%s\")", key),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition hasParam(String key, String value) {
            Preconditions.nonBlank(key, "key");
            Preconditions.nonNull(value, "value");
            return new ImpCondition(
                    GROUP,
                    request -> Objects.equals(request.uri().query().get(key), value),
                    () -> String.format("hasParam(\"%s\")", key),
                    ImpCondition.Kind.CONDITION);
        }
    }

    public static final class Path {

        private static final String GROUP = "Path";

        Path() {}

        public ImpCondition matches(@Language("regexp") String pattern) {
            Preconditions.nonBlank(pattern, "pattern");
            var compiledPattern = Pattern.compile(pattern);
            return new ImpCondition(
                    GROUP,
                    request ->
                            compiledPattern.matcher(request.uri().uriString()).matches(),
                    () -> String.format("matches(\"%s\")", pattern),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition contains(String substring) {
            Preconditions.nonBlank(substring, "substring");
            return new ImpCondition(
                    GROUP,
                    request -> request.uri().uriString().contains(substring),
                    () -> String.format("contains(\"%s\")", substring),
                    ImpCondition.Kind.CONDITION);
        }

        public ImpCondition containsIgnoreCase(String substring) {
            Preconditions.nonBlank(substring, "substring");
            return new ImpCondition(
                    GROUP,
                    request -> request.uri()
                            .uriString()
                            .toLowerCase(Locale.ROOT)
                            .contains(substring.toLowerCase(Locale.ROOT)),
                    () -> String.format("containsIgnoreCase(\"%s\")", substring),
                    ImpCondition.Kind.CONDITION);
        }
    }

    private static List<ImpCondition> verifiedConditions(ImpCondition[] conditions) {
        Preconditions.nonNull(conditions, "conditions");
        var conditionList = Arrays.asList(conditions);
        Preconditions.notEmptyIterable(conditionList, "conditions");
        Preconditions.noNullsInIterable(conditionList, "conditions");
        for (var impCondition : conditionList) {
            if (impCondition == EVERYTHING_INSTANCE) {
                throw new IllegalArgumentException("*Everything* matcher is allowed only on top level");
            }
        }
        return conditionList;
    }
}
