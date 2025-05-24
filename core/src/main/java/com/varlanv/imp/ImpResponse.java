package com.varlanv.imp;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Range;

public final class ImpResponse {

    private final NamedSupplier<byte[]> body;
    private final ImpHttpStatus statusCode;
    private final ImpHeadersOperator headersOperator;

    ImpResponse(NamedSupplier<byte[]> body, ImpHttpStatus statusCode, ImpHeadersOperator headersOperator) {
        this.body = body;
        this.statusCode = statusCode;
        this.headersOperator = headersOperator;
    }

   public ImpSupplier<byte[]> body() {
        return body;
    }

    NamedSupplier<byte[]> trustedBody() {
        return body;
    }

    public ImpHttpStatus statusCode() {
        return statusCode;
    }

    public ImpHeadersOperator headersOperator() {
        return headersOperator;
    }

    public static ImpResponse.BuilderStatus builder() {
        return new BuilderStatus();
    }

    public static final class BuilderStatus {

        public BuilderBody status(@Range(from = 100, to = 511) int statusCode) {
            return new BuilderBody(Preconditions.validHttpStatusCode(statusCode));
        }

        BuilderBody trustedStatus(ImpHttpStatus statusCode) {
            return new BuilderBody(statusCode);
        }
    }

    public static final class BuilderBody {

        private final ImpHttpStatus statusCode;

        BuilderBody(ImpHttpStatus statusCode) {
            this.statusCode = statusCode;
        }

        public BuilderHeaders body(ImpSupplier<byte[]> body) {
            Preconditions.nonNull(body, "body");
            return new BuilderHeaders(statusCode, NamedSupplier.from("body",body), headers -> headers);
        }

         BuilderHeaders trustedBody(NamedSupplier<byte[]> body) {
            Preconditions.nonNull(body, "body");
            return new BuilderHeaders(statusCode, body, headers -> headers);
        }
    }

    public static final class BuilderHeaders {

        private final ImpHttpStatus statusCode;
        private final NamedSupplier<byte[]> body;
        private final ImpHeadersOperator headersOperator;

        BuilderHeaders(ImpHttpStatus statusCode, NamedSupplier<byte[]> body, ImpHeadersOperator headersOperator) {
            this.statusCode = statusCode;
            this.body = body;
            this.headersOperator = headersOperator;
        }

        public BuilderHeaders headers(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new BuilderHeaders(statusCode, body, originalHeaders -> {
                var newHeaders = new HashMap<>(originalHeaders);
                newHeaders.putAll(headersCopy);
                return Collections.unmodifiableMap(newHeaders);
            });
        }

        public ImpResponse build() {
            return new ImpResponse(body, statusCode, headersOperator);
        }

        BuilderHeaders trustedHeaders(ImpHeadersOperator headersOperator) {
            return new BuilderHeaders(statusCode, body, headersOperator);
        }
    }
}
