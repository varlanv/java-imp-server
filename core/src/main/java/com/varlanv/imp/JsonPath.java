package com.varlanv.imp;

final class JsonPath {

    private static final ImpFn<String, ImpFn<String, JsonPathResult>> jsonPathFn;

    static {
        ImpFn<String, ImpFn<String, JsonPathResult>> jsonPathFnTmp;
        try {
            Class.forName("com.jayway.jsonpath.JsonPath");
            jsonPathFnTmp = json -> {
                var parsedJsonSupplier = MemoizedSupplier.of(() -> com.jayway.jsonpath.JsonPath.parse(json));
                return path -> new JaywayJsonPathResult(parsedJsonSupplier, path);
            };
        } catch (ClassNotFoundException e) {
            jsonPathFnTmp = path -> {
                throw new IllegalStateException(
                        "JsonPath library is not found on classpath. "
                                + "Library [ com.jayway.jsonpath:json-path ] is required on classpath to work with jsonPath matchers.");
            };
        }
        jsonPathFn = jsonPathFnTmp;
    }

    private JsonPath() {}

    static ImpFn<String, JsonPathResult> forJson(String json) {
        return jsonPathFn.apply(json);
    }
}
