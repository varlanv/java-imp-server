package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Range;

public final class ImpTemplateSpec {

    public static final class Start {

        public OnRequestMatchingStatus onRequestMatching(String id, ImpConsumer<RequestMatchBuilder> specConsumer) {
            Preconditions.nonNull(specConsumer, "consumer");
            Preconditions.nonBlank(id, "id");
            var builder = new RequestMatchBuilder();
            specConsumer.accept(builder);
            return new OnRequestMatchingStatus(id, builder.build());
        }

        public AlwaysRespondBody alwaysRespondWithStatus(@Range(from = 100, to = 511) int status) {
            return new AlwaysRespondBody(Preconditions.validHttpStatusCode(status));
        }
    }

    public static final class OnRequestMatchingStatus {

        private final String matchId;
        private final RequestMatch requestMatch;

        OnRequestMatchingStatus(String matchId, RequestMatch requestMatch) {
            this.matchId = matchId;
            this.requestMatch = requestMatch;
        }

        public OnRequestMatchingBody respondWithStatus(@Range(from = 100, to = 511) int status) {
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

        public OnRequestMatchingHeaders andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return defaultImpTemplate(ImpContentType.PLAIN_TEXT, () -> textBody.getBytes(StandardCharsets.UTF_8));
        }

        public OnRequestMatchingHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultImpTemplate(ImpContentType.JSON, () -> jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        public OnRequestMatchingHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultImpTemplate(ImpContentType.XML, () -> xmlBody.getBytes(StandardCharsets.UTF_8));
        }

        public OnRequestMatchingHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    ImpContentType.OCTET_STREAM, () -> dataStreamSupplier.get().readAllBytes());
        }

        public OnRequestMatchingHeaders andCustomContentTypeStream(
                String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    contentType, () -> dataStreamSupplier.get().readAllBytes());
        }

        private OnRequestMatchingHeaders defaultImpTemplate(
                CharSequence contentType, ImpSupplier<byte[]> bodySupplier) {
            return new OnRequestMatchingHeaders(this, contentType.toString(), bodySupplier);
        }
    }

    public static final class OnRequestMatchingHeaders {

        private final OnRequestMatchingBody parent;
        private final String contentType;
        private final ImpSupplier<byte[]> bodySupplier;

        public OnRequestMatchingHeaders(
                OnRequestMatchingBody parent, String contentType, ImpSupplier<byte[]> bodySupplier) {
            this.parent = parent;
            this.contentType = contentType;
            this.bodySupplier = bodySupplier;
        }

        public ContentContinue andAdditionalHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInMap(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new ContentContinue(this, existingHeaders -> {
                var newHeaders = new HashMap<>(existingHeaders);
                newHeaders.put("Content-Type", List.of(contentType));
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
            return new ContentContinue(this, headers -> {
                var newHeaders = new HashMap<>(headers);
                newHeaders.put("Content-Type", List.of(contentType));
                return Map.copyOf(newHeaders);
            });
        }
    }

    public static final class ContentContinue {

        private final OnRequestMatchingHeaders parent;
        private final ImpHeadersOperator responseHeadersOperator;

        ContentContinue(OnRequestMatchingHeaders parent, ImpHeadersOperator responseImpHeadersOperator) {
            this.parent = parent;
            this.responseHeadersOperator = responseImpHeadersOperator;
        }

        public ContentFinal fallbackForNonMatching(
                ImpFn<ImpResponse.BuilderStatus, ImpResponse.BuilderHeaders> builderFn) {
            var impResponse = builderFn.apply(ImpResponse.builder()).build();
            return new ContentFinal(this, candidates -> exchange -> impResponse);
        }

        public ContentFinal rejectNonMatching() {
            return new ContentFinal(this, Teapot::new);
        }
    }

    public static final class ContentFinal {

        private final ContentContinue parent;
        private final ImpFn<List<ResponseCandidate>, ImpFn<HttpExchange, ImpResponse>> fallback;

        ContentFinal(
                ContentContinue parent, ImpFn<List<ResponseCandidate>, ImpFn<HttpExchange, ImpResponse>> fallback) {
            this.parent = parent;
            this.fallback = fallback;
        }

        public ImpTemplate onPort(@Range(from = 0, to = Integer.MAX_VALUE) int port) {
            return buildTemplate(PortSupplier.fixed(port), fallback);
        }

        public ImpTemplate onRandomPort() {
            return buildTemplate(PortSupplier.ofSupplier(InternalUtils::randomPort, true), fallback);
        }

        public ImpShared startSharedOnPort(@Range(from = 0, to = Integer.MAX_VALUE) int port) {
            return buildTemplate(PortSupplier.fixed(port), fallback).startShared();
        }

        public ImpShared startSharedOnRandomPort() {
            return buildTemplate(PortSupplier.ofSupplier(InternalUtils::randomPort, true), fallback)
                    .startShared();
        }

        private DefaultImpTemplate buildTemplate(
                PortSupplier portSupplier, ImpFn<List<ResponseCandidate>, ImpFn<HttpExchange, ImpResponse>> fallback) {
            var requestMatch = parent.parent.parent.parent.requestMatch;
            var candidates = List.of(new ResponseCandidate(
                    parent.parent.parent.parent.matchId,
                    request -> requestMatch.headersPredicate().test(new ImpHeadersMatch(request.getRequestHeaders())),
                    () -> ImpResponse.builder()
                            .trustedStatus(parent.parent.parent.status)
                            .body(parent.parent.bodySupplier)
                            .trustedHeaders(parent.responseHeadersOperator)
                            .build()));
            return new DefaultImpTemplate(ImmutableTemplateConfig.builder()
                    .futureServer(new FutureServer(portSupplier))
                    .decision(new ResponseDecision(candidates))
                    .fallback(fallback.apply(candidates))
                    .build());
        }
    }

    public static final class AlwaysRespondBody {

        private final ImpHttpStatus status;

        AlwaysRespondBody(ImpHttpStatus status) {
            this.status = status;
        }

        public AlwaysRespondHeaders andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return defaultRespondHeaders(ImpContentType.PLAIN_TEXT, () -> textBody.getBytes(StandardCharsets.UTF_8));
        }

        public AlwaysRespondHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultRespondHeaders(ImpContentType.JSON, () -> jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        public AlwaysRespondHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultRespondHeaders(ImpContentType.XML, () -> xmlBody.getBytes(StandardCharsets.UTF_8));
        }

        public AlwaysRespondHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultRespondHeaders(ImpContentType.OCTET_STREAM, () -> {
                try (var stream = dataStreamSupplier.get()) {
                    return stream.readAllBytes();
                }
            });
        }

        public AlwaysRespondHeaders andCustomContentTypeStream(
                String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultRespondHeaders(contentType, () -> {
                try (var stream = dataStreamSupplier.get()) {
                    return stream.readAllBytes();
                }
            });
        }

        private AlwaysRespondHeaders defaultRespondHeaders(CharSequence contentType, ImpSupplier<byte[]> bodySupplier) {
            return new AlwaysRespondHeaders(this, contentType.toString(), bodySupplier);
        }
    }

    public static final class AlwaysRespondHeaders {

        private final AlwaysRespondBody parent;
        private final String contentType;
        private final ImpSupplier<byte[]> bodySupplier;

        public AlwaysRespondHeaders(AlwaysRespondBody parent, String contentType, ImpSupplier<byte[]> bodySupplier) {
            this.parent = parent;
            this.contentType = contentType;
            this.bodySupplier = bodySupplier;
        }

        public AlwaysRespondFinal andAdditionalHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInMap(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new AlwaysRespondFinal(this, existingHeaders -> {
                var newHeaders = new HashMap<>(existingHeaders);
                newHeaders.put("Content-Type", List.of(contentType));
                newHeaders.putAll(headersCopy);
                return Map.copyOf(newHeaders);
            });
        }

        public AlwaysRespondFinal andExactHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInMap(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new AlwaysRespondFinal(this, existingHeaders -> headersCopy);
        }

        public AlwaysRespondFinal andNoAdditionalHeaders() {
            return new AlwaysRespondFinal(this, headers -> {
                var newHeaders = new HashMap<>(headers);
                newHeaders.put("Content-Type", List.of(contentType));
                return Map.copyOf(newHeaders);
            });
        }
    }

    public static final class AlwaysRespondFinal {

        private final AlwaysRespondHeaders parent;
        private final ImpHeadersOperator headersOperator;

        public AlwaysRespondFinal(AlwaysRespondHeaders parent, ImpHeadersOperator headersOperator) {
            this.parent = parent;
            this.headersOperator = headersOperator;
        }

        public ImpTemplate onPort(@Range(from = 0, to = Integer.MAX_VALUE) int port) {
            return buildTemplate(PortSupplier.fixed(port));
        }

        public ImpTemplate onRandomPort() {
            return buildTemplate(PortSupplier.ofSupplier(InternalUtils::randomPort, true));
        }

        public ImpShared startSharedOnPort(@Range(from = 0, to = Integer.MAX_VALUE) int port) {
            return buildTemplate(PortSupplier.fixed(port)).startShared();
        }

        public ImpShared startSharedOnRandomPort() {
            return buildTemplate(PortSupplier.ofSupplier(InternalUtils::randomPort, true))
                    .startShared();
        }

        private DefaultImpTemplate buildTemplate(PortSupplier portSupplier) {
            return new DefaultImpTemplate(ImmutableTemplateConfig.builder()
                    .futureServer(new FutureServer(portSupplier))
                    .decision(new ResponseDecision(
                            new ResponseCandidate(ImpPredicate.alwaysTrue(), () -> ImpResponse.builder()
                                    .trustedStatus(parent.parent.status)
                                    .body(parent.bodySupplier)
                                    .trustedHeaders(headersOperator)
                                    .build())))
                    .fallback(new Teapot(List.of()))
                    .build());
        }
    }
}
