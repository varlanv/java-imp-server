package com.varlanv.imp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class ImpRequestView {

    private final String method;
    private final Map<String, List<String>> headers;
    private final MemoizedSupplier<String> stringBodySupplier;
    private final URI uri;

    ImpRequestView(String method, Map<String, List<String>> headers, ImpSupplier<byte[]> bodySupplier, URI uri) {
        this.method = method;
        this.headers = headers;
        this.stringBodySupplier = MemoizedSupplier.of(() -> new String(bodySupplier.get(), StandardCharsets.UTF_8));
        this.uri = uri;
    }

    public String method() {
        return method;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public String body() {
        return stringBodySupplier.get();
    }

    public URI uri() {
        return uri;
    }
}
