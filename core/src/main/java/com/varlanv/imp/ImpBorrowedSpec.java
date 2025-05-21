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

    AlwaysRespond alwaysRespondWithStatus(@Range(from = 100, to = 511) int status) {
        return new AlwaysRespond(this, Preconditions.validHttpStatusCode(status));
    }

    public static final class AlwaysRespond {

        private final ImpBorrowedSpec parent;
        private final ImpHttpStatus status;

        AlwaysRespond(ImpBorrowedSpec parent, ImpHttpStatus status) {
            this.parent = parent;
            this.status = status;
        }

        AlwaysHeaders andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return defaultImpTemplate(ImpContentType.PLAIN_TEXT, () -> textBody.getBytes(StandardCharsets.UTF_8));
        }

        AlwaysHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultImpTemplate(ImpContentType.JSON, () -> jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        AlwaysHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultImpTemplate(ImpContentType.XML, () -> xmlBody.getBytes(StandardCharsets.UTF_8));
        }

        AlwaysHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    ImpContentType.OCTET_STREAM, () -> dataStreamSupplier.get().readAllBytes());
        }

        AlwaysHeaders andCustomContentTypeStream(String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    contentType, () -> dataStreamSupplier.get().readAllBytes());
        }

        private AlwaysHeaders defaultImpTemplate(CharSequence contentType, ImpSupplier<byte[]> bodySupplier) {
            return new AlwaysHeaders(this, contentType.toString(), bodySupplier);
        }
    }

    public static final class AlwaysHeaders {

        private final AlwaysRespond parent;
        private final String contentType;
        private final ImpSupplier<byte[]> bodySupplier;

        public AlwaysHeaders(AlwaysRespond parent, String contentType, ImpSupplier<byte[]> bodySupplier) {
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
            Preconditions.noNullsInMap(headers, "headers");
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
                    ImmutableServerConfig.builder()
                            .decision(new ResponseDecision(
                                    List.of(new ResponseCandidate(ImpPredicate.alwaysTrue(), () -> ImpResponse.builder()
                                            .trustedStatus(parent.status)
                                            .body(bodySupplier)
                                            .trustedHeaders(headersOperator)
                                            .build()))))
                            .port(parent.parent.parent.config().port())
                            .fallback(new Teapot(List.of()))
                            .build(),
                    parent.parent.parent);
        }
    }
}
