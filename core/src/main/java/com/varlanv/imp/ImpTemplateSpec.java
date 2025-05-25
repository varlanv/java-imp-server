package com.varlanv.imp;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Range;

public final class ImpTemplateSpec {

    public static final class Start {

        public OnRequestMatchingStatus onRequestMatching(String id, ImpConsumer<RequestMatchBuilder> specConsumer) {
            Preconditions.nonNull(specConsumer, "consumer");
            Preconditions.nonBlank(id, "id");
            var builder = new RequestMatchBuilder();
            specConsumer.accept(builder);
            return new OnRequestMatchingStatus(id, builder.build(), List.of());
        }

        public AlwaysRespondBody alwaysRespondWithStatus(@Range(from = 100, to = 511) int status) {
            return new AlwaysRespondBody(Preconditions.validHttpStatusCode(status));
        }
    }

    public static final class OnRequestMatchingStatus {

        private final String matchId;
        private final RequestMatch requestMatch;
        private final List<ResponseCandidate> responseCandidates;

        OnRequestMatchingStatus(String matchId, RequestMatch requestMatch, List<ResponseCandidate> responseCandidates) {
            this.matchId = matchId;
            this.requestMatch = requestMatch;
            this.responseCandidates = responseCandidates;
        }

        public OnRequestMatchingBody respondWithStatus(@Range(from = 100, to = 511) int status) {
            return new OnRequestMatchingBody(
                    matchId, requestMatch, Preconditions.validHttpStatusCode(status), responseCandidates);
        }
    }

    public static final class OnRequestMatchingBody {

        private final String matchId;
        private final RequestMatch requestMatch;
        private final ImpHttpStatus status;
        private final List<ResponseCandidate> responseCandidates;

        OnRequestMatchingBody(
                String matchId,
                RequestMatch requestMatch,
                ImpHttpStatus status,
                List<ResponseCandidate> responseCandidates) {
            this.matchId = matchId;
            this.requestMatch = requestMatch;
            this.status = status;
            this.responseCandidates = responseCandidates;
        }

        public OnRequestMatchingHeaders andBodyBasedOnRequest(
                String contentType, ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(bodyFunction, "bodyFunction");
            return toHeadersMatching(contentType, bodyFunction);
        }

        public OnRequestMatchingHeaders andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return toHeadersMatching(
                    ImpContentType.PLAIN_TEXT,
                    ignored -> () -> new ByteArrayInputStream(textBody.getBytes(StandardCharsets.UTF_8)));
        }

        public OnRequestMatchingHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return toHeadersMatching(
                    ImpContentType.JSON,
                    ignored -> () -> new ByteArrayInputStream(jsonBody.getBytes(StandardCharsets.UTF_8)));
        }

        public OnRequestMatchingHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return toHeadersMatching(
                    ImpContentType.XML,
                    ignored -> () -> new ByteArrayInputStream(xmlBody.getBytes(StandardCharsets.UTF_8)));
        }

        public OnRequestMatchingHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return toHeadersMatching(ImpContentType.OCTET_STREAM, ignored -> dataStreamSupplier);
        }

        public OnRequestMatchingHeaders andCustomContentTypeStream(
                String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return toHeadersMatching(contentType, ignored -> dataStreamSupplier);
        }

        private OnRequestMatchingHeaders toHeadersMatching(
                CharSequence contentType, ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodySupplier) {
            return new OnRequestMatchingHeaders(
                    matchId, requestMatch, status, contentType.toString(), bodySupplier, responseCandidates);
        }
    }

    public static final class OnRequestMatchingHeaders {

        private final String matchId;
        private final RequestMatch requestMatch;
        private final ImpHttpStatus status;
        private final String contentType;
        private final ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction;
        private final List<ResponseCandidate> responseCandidates;

        OnRequestMatchingHeaders(
                String matchId,
                RequestMatch requestMatch,
                ImpHttpStatus status,
                String contentType,
                ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction,
                List<ResponseCandidate> responseCandidates) {
            this.matchId = matchId;
            this.requestMatch = requestMatch;
            this.status = status;
            this.contentType = contentType;
            this.bodyFunction = bodyFunction;
            this.responseCandidates = responseCandidates;
        }

        public ContentContinue andAdditionalHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new ContentContinue(
                    matchId, requestMatch, status, bodyFunction, responseCandidates, existingHeaders -> {
                        var newHeaders = new HashMap<>(existingHeaders);
                        newHeaders.put("Content-Type", List.of(contentType));
                        newHeaders.putAll(headersCopy);
                        return Map.copyOf(newHeaders);
                    });
        }

        public ContentContinue andExactHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new ContentContinue(
                    matchId, requestMatch, status, bodyFunction, responseCandidates, existingHeaders -> headersCopy);
        }

        public ContentContinue andNoAdditionalHeaders() {
            return new ContentContinue(matchId, requestMatch, status, bodyFunction, responseCandidates, headers -> {
                var newHeaders = new HashMap<>(headers);
                newHeaders.put("Content-Type", List.of(contentType));
                return Map.copyOf(newHeaders);
            });
        }
    }

    public static final class ContentContinue {

        private final String matchId;
        private final RequestMatch requestMatch;
        private final ImpHttpStatus status;
        private final ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction;
        private final List<ResponseCandidate> responseCandidates;
        private final ImpHeadersOperator responseHeadersOperator;

        ContentContinue(
                String matchId,
                RequestMatch requestMatch,
                ImpHttpStatus status,
                ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction,
                List<ResponseCandidate> responseCandidates,
                ImpHeadersOperator responseHeadersOperator) {
            this.matchId = matchId;
            this.requestMatch = requestMatch;
            this.status = status;
            this.bodyFunction = bodyFunction;
            this.responseHeadersOperator = responseHeadersOperator;
            this.responseCandidates = responseCandidates;
        }

        public AlsoOnMatching alsoOnRequestMatching(String id, ImpConsumer<RequestMatchBuilder> specConsumer) {
            Preconditions.nonNull(specConsumer, "consumer");
            Preconditions.nonBlank(id, "id");
            var builder = new RequestMatchBuilder();
            specConsumer.accept(builder);
            var requestMatch = builder.build();
            return new AlsoOnMatching(
                    id, requestMatch, InternalUtils.addToNewListFinal(responseCandidates, buildResponseCandidate()));
        }

        public ContentFinal fallbackForNonMatching(
                ImpFn<ImpResponse.BuilderStatus, ImpResponse.BuilderHeaders> fallbackFn) {
            Preconditions.nonNull(fallbackFn, "fallbackFn");
            var impResponse = fallbackFn.apply(ImpResponse.builder()).build();
            return new ContentFinal(
                    InternalUtils.addToNewListFinal(responseCandidates, buildResponseCandidate()),
                    candidates -> exchange -> impResponse);
        }

        public ContentFinal rejectNonMatching() {
            return new ContentFinal(
                    InternalUtils.addToNewListFinal(responseCandidates, buildResponseCandidate()), Teapot::new);
        }

        private ResponseCandidate buildResponseCandidate() {
            return new ResponseCandidate(
                    matchId,
                    request -> requestMatch.headersPredicate().test(new ImpHeadersMatch(request.getRequestHeaders()))
                            && requestMatch.bodyPredicate().test(new ImpBodyMatch(request::getRequestBody))
                            && requestMatch.urlPredicate().test(new ImpUrlMatch(request.getRequestURI())),
                    () -> ImpResponse.builder()
                            .trustedStatus(status)
                            .bodyFromRequest(bodyFunction)
                            .trustedHeaders(responseHeadersOperator)
                            .build());
        }
    }

    public static final class AlsoOnMatching {

        private final String matchId;
        private final RequestMatch requestMatch;
        private final List<ResponseCandidate> responseCandidates;

        AlsoOnMatching(String matchId, RequestMatch requestMatch, List<ResponseCandidate> responseCandidates) {
            this.matchId = matchId;
            this.requestMatch = requestMatch;
            this.responseCandidates = responseCandidates;
        }

        public OnRequestMatchingBody respondWithStatus(@Range(from = 100, to = 511) int status) {
            return new OnRequestMatchingBody(
                    matchId, requestMatch, Preconditions.validHttpStatusCode(status), responseCandidates);
        }
    }

    public static final class ContentFinal {

        private final List<ResponseCandidate> responseCandidates;
        private final ImpFn<List<ResponseCandidate>, ImpFn<HttpExchange, ImpResponse>> fallback;

        ContentFinal(
                List<ResponseCandidate> responseCandidates,
                ImpFn<List<ResponseCandidate>, ImpFn<HttpExchange, ImpResponse>> fallback) {
            this.responseCandidates = responseCandidates;
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
            return new DefaultImpTemplate(ImmutableTemplateConfig.builder()
                    .futureServer(new FutureServer(portSupplier))
                    .decision(new ResponseDecision(responseCandidates))
                    .fallback(fallback.apply(responseCandidates))
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
            return defaultRespondHeaders(
                    ImpContentType.PLAIN_TEXT,
                    ignored -> () -> new ByteArrayInputStream(textBody.getBytes(StandardCharsets.UTF_8)));
        }

        public AlwaysRespondHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultRespondHeaders(
                    ImpContentType.JSON,
                    ignored -> () -> new ByteArrayInputStream(jsonBody.getBytes(StandardCharsets.UTF_8)));
        }

        public AlwaysRespondHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultRespondHeaders(
                    ImpContentType.XML,
                    ignored -> () -> new ByteArrayInputStream(xmlBody.getBytes(StandardCharsets.UTF_8)));
        }

        public AlwaysRespondHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultRespondHeaders(ImpContentType.OCTET_STREAM, ignored -> dataStreamSupplier);
        }

        public AlwaysRespondHeaders andCustomContentTypeStream(
                String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultRespondHeaders(contentType, ignored -> dataStreamSupplier);
        }

        private AlwaysRespondHeaders defaultRespondHeaders(
                CharSequence contentType, ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction) {
            return new AlwaysRespondHeaders(status, contentType.toString(), bodyFunction);
        }
    }

    public static final class AlwaysRespondHeaders {

        private final ImpHttpStatus status;
        private final String contentType;
        private final ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction;

        public AlwaysRespondHeaders(
                ImpHttpStatus status,
                String contentType,
                ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction) {
            this.status = status;
            this.contentType = contentType;
            this.bodyFunction = bodyFunction;
        }

        public AlwaysRespondFinal andAdditionalHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new AlwaysRespondFinal(status, bodyFunction, existingHeaders -> {
                var newHeaders = new Headers();
                newHeaders.putAll(headersCopy);
                newHeaders.put("Content-Type", List.of(contentType));
                newHeaders.putAll(existingHeaders);
                return Map.copyOf(newHeaders);
            });
        }

        public AlwaysRespondFinal andExactHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new AlwaysRespondFinal(status, bodyFunction, existingHeaders -> headersCopy);
        }

        public AlwaysRespondFinal andNoAdditionalHeaders() {
            return new AlwaysRespondFinal(status, bodyFunction, headers -> {
                var newHeaders = new Headers();
                newHeaders.putAll(headers);
                newHeaders.put("Content-Type", List.of(contentType));
                return Map.copyOf(newHeaders);
            });
        }
    }

    public static final class AlwaysRespondFinal {

        private final ImpHttpStatus status;
        private final ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction;
        private final ImpHeadersOperator headersOperator;

        public AlwaysRespondFinal(
                ImpHttpStatus status,
                ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction,
                ImpHeadersOperator headersOperator) {
            this.status = status;
            this.bodyFunction = bodyFunction;
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
                                    .trustedStatus(status)
                                    .bodyFromRequest(bodyFunction)
                                    .trustedHeaders(headersOperator)
                                    .build())))
                    .fallback(new Teapot(List.of()))
                    .build());
        }
    }
}
