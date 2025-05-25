package com.varlanv.imp;

import com.sun.net.httpserver.Headers;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Range;

public final class ImpBorrowedSpec {

    private final DefaultImpShared parent;

    ImpBorrowedSpec(DefaultImpShared parent) {
        this.parent = parent;
    }

    public AlwaysRespond alwaysRespondWithStatus(@Range(from = 100, to = 511) int status) {
        return new AlwaysRespond(this, Preconditions.validHttpStatusCode(status));
    }

    public static final class AlwaysRespond {

        private final ImpBorrowedSpec parent;
        private final ImpHttpStatus status;

        AlwaysRespond(ImpBorrowedSpec parent, ImpHttpStatus status) {
            this.parent = parent;
            this.status = status;
        }

        public AlwaysHeaders andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return defaultImpTemplate(
                    ImpContentType.PLAIN_TEXT,
                    NamedFn.from(
                            "andTextBody",
                            ignored -> () -> new ByteArrayInputStream(textBody.getBytes(StandardCharsets.UTF_8))));
        }

        public AlwaysHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultImpTemplate(
                    ImpContentType.JSON,
                    NamedFn.from(
                            "andJsonBody",
                            ignored -> () -> new ByteArrayInputStream(jsonBody.getBytes(StandardCharsets.UTF_8))));
        }

        public AlwaysHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultImpTemplate(
                    ImpContentType.XML,
                    NamedFn.from(
                            "andXmlBody",
                            ignored -> () -> new ByteArrayInputStream(xmlBody.getBytes(StandardCharsets.UTF_8))));
        }

        public AlwaysHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    ImpContentType.OCTET_STREAM, NamedFn.from("andDataStreamBody", ignored -> dataStreamSupplier));
        }

        public AlwaysHeaders andCustomContentTypeStream(
                String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    contentType, NamedFn.from("andCustomContentTypeStream", ignored -> dataStreamSupplier));
        }

        private AlwaysHeaders defaultImpTemplate(
                CharSequence contentType, NamedFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction) {
            return new AlwaysHeaders(this, contentType.toString(), bodyFunction);
        }
    }

    public static final class AlwaysHeaders {

        private final AlwaysRespond parent;
        private final String contentType;
        private final NamedFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction;

        AlwaysHeaders(
                AlwaysRespond parent,
                String contentType,
                NamedFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction) {
            this.parent = parent;
            this.contentType = contentType;
            this.bodyFunction = bodyFunction;
        }

        public ImpBorrowed andNoAdditionalHeaders() {
            return toBorrowed(headers -> {
                var res = new HashMap<>(headers);
                res.put("Content-Type", List.of(contentType));
                return Collections.unmodifiableMap(res);
            });
        }

        public ImpBorrowed andHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return toBorrowed(originalHeaders -> {
                var newHeaders = new Headers();
                newHeaders.putAll(originalHeaders);
                newHeaders.put("Content-Type", List.of(contentType));
                newHeaders.putAll(headersCopy);
                return Collections.unmodifiableMap(newHeaders);
            });
        }

        private ImpBorrowed toBorrowed(ImpHeadersOperator headersOperator) {
            return new ImpBorrowed(
                    ImmutableStartedServerConfig.builder()
                            .server(parent.parent.parent.config().server())
                            .decision(new ResponseDecision(
                                    List.of(new ResponseCandidate(ImpPredicate.alwaysTrue(), () -> ImpResponse.builder()
                                            .trustedStatus(parent.status)
                                            .trustedBody(bodyFunction)
                                            .trustedHeaders(headersOperator)
                                            .build()))))
                            .fallback(new Teapot(List.of()))
                            .build(),
                    parent.parent.parent);
        }
    }
}
