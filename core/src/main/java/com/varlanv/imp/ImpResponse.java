package com.varlanv.imp;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Range;

public final class ImpResponse {

    private final NamedFn<ImpRequestView, ImpSupplier<InputStream>> body;
    private final ImpHttpStatus statusCode;
    private final ImpHeadersOperator headersOperator;

    ImpResponse(
            NamedFn<ImpRequestView, ImpSupplier<InputStream>> body,
            ImpHttpStatus statusCode,
            ImpHeadersOperator headersOperator) {
        this.body = body;
        this.statusCode = statusCode;
        this.headersOperator = headersOperator;
    }

    public ImpFn<ImpRequestView, ImpSupplier<InputStream>> body() {
        return body;
    }

    NamedFn<ImpRequestView, ImpSupplier<InputStream>> trustedBody() {
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

        BuilderStatus() {}

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

        public BuilderHeaders body(ImpSupplier<InputStream> bodySupplier) {
            Preconditions.nonNull(bodySupplier, "bodySupplier");
            return new BuilderHeaders(statusCode, NamedFn.from("body", ignored -> bodySupplier), headers -> headers);
        }

        public BuilderHeaders bodyFromRequest(ImpFn<ImpRequestView, ImpSupplier<InputStream>> body) {
            Preconditions.nonNull(body, "body");
            return new BuilderHeaders(statusCode, NamedFn.from("body", body), headers -> headers);
        }

        BuilderHeaders trustedBody(NamedFn<ImpRequestView, ImpSupplier<InputStream>> body) {
            Preconditions.nonNull(body, "body");
            return new BuilderHeaders(statusCode, body, headers -> headers);
        }
    }

    public static final class BuilderHeaders {

        private final ImpHttpStatus statusCode;
        private final NamedFn<ImpRequestView, ImpSupplier<InputStream>> body;
        private final ImpHeadersOperator headersOperator;

        BuilderHeaders(
                ImpHttpStatus statusCode,
                NamedFn<ImpRequestView, ImpSupplier<InputStream>> body,
                ImpHeadersOperator headersOperator) {
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
