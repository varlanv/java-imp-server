package com.varlanv.imp;

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
                    NamedSupplier.from("andTextBody", () -> textBody.getBytes(StandardCharsets.UTF_8)));
        }

        public AlwaysHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultImpTemplate(
                    ImpContentType.JSON,
                    NamedSupplier.from("andJsonBody", () -> jsonBody.getBytes(StandardCharsets.UTF_8)));
        }

        public AlwaysHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultImpTemplate(
                    ImpContentType.XML,
                    NamedSupplier.from("andXmlBody", () -> xmlBody.getBytes(StandardCharsets.UTF_8)));
        }

        public AlwaysHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    ImpContentType.OCTET_STREAM,
                    NamedSupplier.from(
                            "andDataStreamBody", () -> dataStreamSupplier.get().readAllBytes()));
        }

        public AlwaysHeaders andCustomContentTypeStream(
                String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    contentType, NamedSupplier.from("andCustomContentTypeStream", () -> dataStreamSupplier
                            .get()
                            .readAllBytes()));
        }

        private AlwaysHeaders defaultImpTemplate(CharSequence contentType, NamedSupplier<byte[]> bodySupplier) {
            return new AlwaysHeaders(this, contentType.toString(), bodySupplier);
        }
    }

    public static final class AlwaysHeaders {

        private final AlwaysRespond parent;
        private final String contentType;
        private final NamedSupplier<byte[]> bodySupplier;

        AlwaysHeaders(AlwaysRespond parent, String contentType, NamedSupplier<byte[]> bodySupplier) {
            this.parent = parent;
            this.contentType = contentType;
            this.bodySupplier = bodySupplier;
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
                var res = new HashMap<>(originalHeaders);
                res.put("Content-Type", List.of(contentType));
                res.putAll(headersCopy);
                return Collections.unmodifiableMap(res);
            });
        }

        private ImpBorrowed toBorrowed(ImpHeadersOperator headersOperator) {
            return new ImpBorrowed(
                    ImmutableStartedServerConfig.builder()
                            .server(parent.parent.parent.config().server())
                            .decision(new ResponseDecision(
                                    List.of(new ResponseCandidate(ImpPredicate.alwaysTrue(), () -> ImpResponse.builder()
                                            .trustedStatus(parent.status)
                                            .trustedBody(bodySupplier)
                                            .trustedHeaders(headersOperator)
                                            .build()))))
                            .fallback(new Teapot(List.of()))
                            .build(),
                    parent.parent.parent);
        }
    }
}
