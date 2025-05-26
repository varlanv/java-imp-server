package com.varlanv.imp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import org.jspecify.annotations.Nullable;

final class JsonPathInternal {

    private static final ImpFn<String, JsonPathMatch> jsonPathFn;
    private static final ImpFn<String, CompiledJson> compiledJsonFn;

    static {
        ImpFn<String, JsonPathMatch> jsonPathFnTmp;
        ImpFn<String, CompiledJson> compiledJsonFnTmp;
        try {
            Class.forName("com.jayway.jsonpath.JsonPath");
            jsonPathFnTmp = jsonPathString -> {
                try {
                    var compiledJsonPath = JsonPath.compile(jsonPathString);
                    return new JaywayJsonPathMatch(new CompiledPath(compiledJsonPath));
                } catch (InvalidPathException e) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Provided invalid JsonPath - [ %s ]. " + "Check internal error message for details",
                                    jsonPathString),
                            e);
                }
            };
            compiledJsonFnTmp = json -> {
                var compiledJsonPath = JsonPath.parse(json);
                return new CompiledJson(compiledJsonPath);
            };
        } catch (ClassNotFoundException e) {
            var message = "JsonPath library is not found on classpath. "
                    + "Library [ com.jayway.jsonpath:json-path ] is required on classpath to work with jsonPath matchers.";
            jsonPathFnTmp = path -> {
                throw new IllegalStateException(message);
            };
            compiledJsonFnTmp = json -> {
                throw new IllegalStateException(message);
            };
        }
        jsonPathFn = jsonPathFnTmp;
        compiledJsonFn = compiledJsonFnTmp;
    }

    private JsonPathInternal() {}

    static JsonPathMatch forJsonPath(String jsonPath) {
        return jsonPathFn.apply(jsonPath);
    }

    static CompiledJson compileJson(String json) {
        return compiledJsonFn.apply(json);
    }

    static class CompiledJson {

        final DocumentContext documentContext;

        CompiledJson(DocumentContext documentContext) {
            this.documentContext = documentContext;
        }
    }

    static class CompiledPath {

        final JsonPath jsonPath;

        CompiledPath(JsonPath jsonPath) {
            this.jsonPath = jsonPath;
        }
    }

    static final class ResultRef {

        @Nullable final Object value;

        final boolean isPresent;

        ResultRef(@Nullable Object value, boolean isPresent) {
            this.value = value;
            this.isPresent = isPresent;
        }
    }
}
