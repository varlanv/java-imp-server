package com.varlanv.imp;

import com.jayway.jsonpath.PathNotFoundException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.intellij.lang.annotations.MagicConstant;

public final class ImpRequestView {

    private final String method;
    private final Map<String, List<String>> headers;
    private final MemoizedSupplier<String> stringBodySupplier;
    private final MemoizedSupplier<JsonPathInternal.CompiledJson> compiledJsonSupplier;
    private final ImpFn<JsonPathInternal.CompiledPath, JsonPathInternal.ResultRef> jsonPathValueFn;
    private final ImpUri uri;

    ImpRequestView(
            @MagicConstant(stringValues = {"GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH"})
                    String method,
            Map<String, List<String>> headers,
            ImpSupplier<byte[]> bodySupplier,
            URI uri) {
        this.method = method;
        this.headers = headers;
        this.stringBodySupplier = MemoizedSupplier.of(() -> new String(bodySupplier.get(), StandardCharsets.UTF_8));
        this.compiledJsonSupplier = MemoizedSupplier.of(() -> JsonPathInternal.compileJson(stringBodySupplier.get()));
        this.jsonPathValueFn = compiledPath -> {
            try {
                var result = compiledJsonSupplier.get().documentContext.read(compiledPath.jsonPath);
                return new JsonPathInternal.ResultRef(result, true);
            } catch (PathNotFoundException e) {
                return new JsonPathInternal.ResultRef(null, false);
            }
        };
        this.uri = new ImpUri(uri);
    }

    @MagicConstant(stringValues = {"GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH"})
    public String method() {
        //noinspection MagicConstant
        return method;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public String body() {
        return stringBodySupplier.get();
    }

    public ImpUri uri() {
        return uri;
    }

    JsonPathInternal.ResultRef jsonPathResultRef(JsonPathInternal.CompiledPath compiledPath) {
        return jsonPathValueFn.apply(compiledPath);
    }
}
