package com.varlanv.imp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Range;

public final class ImpTemplateSpec {

    public static final class Start {

        ContentStart randomPort() {
            return new ContentStart(new ImpPort(InternalUtils::randomPort, true));
        }

        ContentStart port(int port) {
            return new ContentStart(new ImpPort(() -> port, false));
        }
    }

    public static final class ContentStart {

        private final ImpPort port;

        ContentStart(ImpPort port) {
            this.port = port;
        }

        OnRequestMatchingStatus onRequestMatching(String id, ImpConsumer<RequestMatchBuilder> specConsumer) {
            Preconditions.nonNull(specConsumer, "consumer");
            Preconditions.nonBlank(id, "id");
            var builder = new RequestMatchBuilder();
            specConsumer.accept(builder);
            return new OnRequestMatchingStatus(id, this, builder.build());
        }

        AlwaysRespond alwaysRespondWithStatus(@Range(from = 100, to = 511) int status) {
            return new AlwaysRespond(this, Preconditions.validHttpStatusCode(status));
        }
    }

    public static final class OnRequestMatchingStatus {

        private final String matchId;
        private final ContentStart parent;
        private final RequestMatch requestMatch;

        OnRequestMatchingStatus(String matchId, ContentStart contentStart, RequestMatch requestMatch) {
            this.matchId = matchId;
            this.parent = contentStart;
            this.requestMatch = requestMatch;
        }

        OnRequestMatchingBody respondWithStatus(@Range(from = 100, to = 511) int status) {
            return new OnRequestMatchingBody(this, Preconditions.validHttpStatusCode(status));
        }
    }

    public static final class OnRequestMatchingBody {

        private final OnRequestMatchingStatus parent;
        private final ImpHttpStatus status;

        OnRequestMatchingBody(OnRequestMatchingStatus parent, ImpHttpStatus status) {
            this.parent = parent;
            this.status = status;
        }

        OnRequestMatchingHeaders andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return defaultImpTemplate(ImpContentType.PLAIN_TEXT, () -> textBody.getBytes(StandardCharsets.UTF_8));
        }

        OnRequestMatchingHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultImpTemplate(ImpContentType.JSON, () -> jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        OnRequestMatchingHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultImpTemplate(ImpContentType.XML, () -> xmlBody.getBytes(StandardCharsets.UTF_8));
        }

        OnRequestMatchingHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    ImpContentType.OCTET_STREAM, () -> dataStreamSupplier.get().readAllBytes());
        }

        OnRequestMatchingHeaders andCustomContentTypeStream(
                String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    contentType, () -> dataStreamSupplier.get().readAllBytes());
        }

        private OnRequestMatchingHeaders defaultImpTemplate(
                CharSequence contentType, ImpSupplier<byte[]> bodySupplier) {
            return new OnRequestMatchingHeaders(this, () -> Map.entry(contentType.toString(), bodySupplier));
        }
    }

    public static final class OnRequestMatchingHeaders {

        private final OnRequestMatchingBody parent;
        private final ImpSupplier<Map.Entry<String, ImpSupplier<byte[]>>> bodyAndContentTypeSupplier;

        public OnRequestMatchingHeaders(
                OnRequestMatchingBody parent,
                ImpSupplier<Map.Entry<String, ImpSupplier<byte[]>>> bodyAndContentTypeSupplier) {
            this.parent = parent;
            this.bodyAndContentTypeSupplier = bodyAndContentTypeSupplier;
        }

        public ContentContinue andAdditionalHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInMap(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new ContentContinue(this, existingHeaders -> {
                var newHeaders = new HashMap<>(existingHeaders);
                newHeaders.putAll(headersCopy);
                return Map.copyOf(newHeaders);
            });
        }

        public ContentContinue andExactHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInMap(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new ContentContinue(this, existingHeaders -> headersCopy);
        }

        public ContentContinue andNoAdditionalHeaders() {
            return new ContentContinue(this, headers -> headers);
        }
    }

    public static final class ContentContinue {

        private final OnRequestMatchingHeaders parent;
        private final HeadersOperator responseHeadersOperator;

        ContentContinue(OnRequestMatchingHeaders parent, HeadersOperator responseHeadersOperator) {
            this.parent = parent;
            this.responseHeadersOperator = responseHeadersOperator;
        }

        public ImpTemplate rejectNonMatching() {
            return new DefaultImpTemplate(ImmutableServerConfig.builder()
                    .port(parent.parent.parent.parent.port)
                    .decision(new ResponseDecision(List.of(new ResponseCandidate(
                            parent.parent.parent.matchId,
                            request -> parent.parent
                                    .parent
                                    .requestMatch
                                    .headersPredicate()
                                    .test(new ImpHeadersMatch(request.getRequestHeaders())),
                            () -> {
                                var responseEntry = parent.bodyAndContentTypeSupplier.get();
                                var headers = new HashMap<String, List<String>>();
                                headers.put("Content-Type", List.of(responseEntry.getKey()));
                                return ImmutableImpResponse.builder()
                                        .headers(Collections.unmodifiableMap(responseHeadersOperator.apply(headers)))
                                        .statusCode(parent.parent.status)
                                        .body(responseEntry.getValue())
                                        .build();
                            }))))
                    .build());
        }
    }

    public static final class AlwaysRespond {

        private final ContentStart parent;
        private final ImpHttpStatus status;

        AlwaysRespond(ContentStart parent, ImpHttpStatus status) {
            this.parent = parent;
            this.status = status;
        }

        ImpTemplate andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return defaultImpTemplate(ImpContentType.PLAIN_TEXT, () -> textBody.getBytes(StandardCharsets.UTF_8));
        }

        ImpTemplate andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultImpTemplate(ImpContentType.JSON, () -> jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        ImpTemplate andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultImpTemplate(ImpContentType.XML, () -> xmlBody.getBytes(StandardCharsets.UTF_8));
        }

        ImpTemplate andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    ImpContentType.OCTET_STREAM, () -> dataStreamSupplier.get().readAllBytes());
        }

        ImpTemplate andCustomContentTypeStream(String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    contentType, () -> dataStreamSupplier.get().readAllBytes());
        }

        private DefaultImpTemplate defaultImpTemplate(CharSequence contentType, ImpSupplier<byte[]> bodySupplier) {
            return new DefaultImpTemplate(ImmutableServerConfig.builder()
                    .port(parent.port)
                    .decision(new ResponseDecision(
                            new ResponseCandidate(ImpPredicate.alwaysTrue(), () -> ImmutableImpResponse.builder()
                                    .body(bodySupplier)
                                    .headers(Map.of("Content-Type", List.of(contentType.toString())))
                                    .statusCode(status)
                                    .build())))
                    .build());
        }
    }
}
