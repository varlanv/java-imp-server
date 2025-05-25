package com.varlanv.imp;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Range;

public final class ImpTemplateSpec {

    public static final class Start {

        public SpecFinal alwaysRespond(ImpAlwaysRespond action) {
            Preconditions.nonNull(action, "action");
            var specEnd = action.apply(new AlwaysRespondSpecStart());
            Preconditions.nonNull(specEnd, "alwaysRespond function result");
            return new SpecFinal(List.of(specEnd.toResponseCandidate()), ignored -> new Teapot(List.of()));
        }

        public RequestMatchingSpecContinue matchRequest(ImpRequestMatch action) {
            Preconditions.nonNull(action, "action");
            var specEnd = action.apply(new RequestMatchingSpecStart());
            Preconditions.nonNull(specEnd, "matchRequest function result");
            var responseCandidate = specEnd.toResponseCandidate();
            return new RequestMatchingSpecContinue(List.of(responseCandidate));
        }
    }

    public static final class RequestMatchingSpecStart {

        public RequestMatchingSpecId id(String id) {
            Preconditions.nonBlank(id, "id");
            return new RequestMatchingSpecId(id);
        }
    }

    public static final class RequestMatchingSpecId {

        private final String id;

        RequestMatchingSpecId(String id) {
            this.id = id;
        }

        public RequestMatchingSpecPriority priority(int priority) {
            return new RequestMatchingSpecPriority(id, priority);
        }
    }

    public static final class RequestMatchingSpecPriority {

        private final String id;

        private final int priority;

        RequestMatchingSpecPriority(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        public RequestMatchingSpecMatch match(ImpConsumer<RequestMatchBuilder> specConsumer) {
            Preconditions.nonNull(specConsumer, "consumer");
            var builder = new RequestMatchBuilder();
            specConsumer.accept(builder);
            return new RequestMatchingSpecMatch(id, priority, builder.build());
        }
    }

    public static final class RequestMatchingSpecMatch {

        private final String id;
        private final int priority;

        private final RequestMatch match;

        RequestMatchingSpecMatch(String id, int priority, RequestMatch match) {
            this.id = id;
            this.priority = priority;
            this.match = match;
        }

        public RequestMatchingSpecStatus respondWithStatus(@Range(from = 100, to = 511) int status) {
            return new RequestMatchingSpecStatus(id, priority, match, Preconditions.validHttpStatusCode(status));
        }
    }

    public static final class RequestMatchingSpecStatus {

        private final String id;
        private final int priority;
        private final RequestMatch match;

        private final ImpHttpStatus responseStatus;

        RequestMatchingSpecStatus(String id, int priority, RequestMatch match, ImpHttpStatus responseStatus) {
            this.id = id;
            this.priority = priority;
            this.match = match;
            this.responseStatus = responseStatus;
        }

        public RequestMatchingSpecHeaders andBodyBasedOnRequest(
                String contentType, ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(bodyFunction, "bodyFunction");
            return toHeadersMatching(contentType, bodyFunction);
        }

        public RequestMatchingSpecHeaders andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return toHeadersMatching(
                    ImpContentType.PLAIN_TEXT,
                    ignored -> () -> new ByteArrayInputStream(textBody.getBytes(StandardCharsets.UTF_8)));
        }

        public RequestMatchingSpecHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return toHeadersMatching(
                    ImpContentType.JSON,
                    ignored -> () -> new ByteArrayInputStream(jsonBody.getBytes(StandardCharsets.UTF_8)));
        }

        public RequestMatchingSpecHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return toHeadersMatching(
                    ImpContentType.XML,
                    ignored -> () -> new ByteArrayInputStream(xmlBody.getBytes(StandardCharsets.UTF_8)));
        }

        public RequestMatchingSpecHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return toHeadersMatching(ImpContentType.OCTET_STREAM, ignored -> dataStreamSupplier);
        }

        public RequestMatchingSpecHeaders andCustomContentTypeStream(
                String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return toHeadersMatching(contentType, ignored -> dataStreamSupplier);
        }

        private RequestMatchingSpecHeaders toHeadersMatching(
                CharSequence contentType, ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction) {
            return new RequestMatchingSpecHeaders(
                    id, priority, match, responseStatus, contentType.toString(), bodyFunction);
        }
    }

    public static final class RequestMatchingSpecHeaders {

        private final String id;
        private final int priority;
        private final RequestMatch match;
        private final ImpHttpStatus responseStatus;
        private final String contentType;

        private final ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction;

        RequestMatchingSpecHeaders(
                String id,
                int priority,
                RequestMatch match,
                ImpHttpStatus responseStatus,
                String contentType,
                ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction) {
            this.id = id;
            this.priority = priority;
            this.match = match;
            this.responseStatus = responseStatus;
            this.contentType = contentType;
            this.bodyFunction = bodyFunction;
        }

        public RequestMatchingSpecEnd andAdditionalHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new RequestMatchingSpecEnd(id, priority, match, responseStatus, bodyFunction, existingHeaders -> {
                var newHeaders = new HashMap<>(existingHeaders);
                newHeaders.put("Content-Type", List.of(contentType));
                newHeaders.putAll(headersCopy);
                return Map.copyOf(newHeaders);
            });
        }

        public RequestMatchingSpecEnd andExactHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new RequestMatchingSpecEnd(
                    id, priority, match, responseStatus, bodyFunction, existingHeaders -> headersCopy);
        }

        public RequestMatchingSpecEnd andNoAdditionalHeaders() {
            return new RequestMatchingSpecEnd(id, priority, match, responseStatus, bodyFunction, headers -> {
                var newHeaders = new HashMap<>(headers);
                newHeaders.put("Content-Type", List.of(contentType));
                return Map.copyOf(newHeaders);
            });
        }
    }

    public static final class RequestMatchingSpecEnd {

        private final String id;
        private final int priority;
        private final RequestMatch requestMatch;
        private final ImpHttpStatus responseStatus;
        private final ImpFn<ImpRequestView, ImpSupplier<InputStream>> responseBodyFunction;

        private final ImpHeadersOperator responseHeadersOperator;

        RequestMatchingSpecEnd(
                String id,
                int priority,
                RequestMatch requestMatch,
                ImpHttpStatus responseStatus,
                ImpFn<ImpRequestView, ImpSupplier<InputStream>> responseBodyFunction,
                ImpHeadersOperator responseHeadersOperator) {
            this.id = id;
            this.priority = priority;
            this.requestMatch = requestMatch;
            this.responseStatus = responseStatus;
            this.responseBodyFunction = responseBodyFunction;
            this.responseHeadersOperator = responseHeadersOperator;
        }

        ResponseCandidate toResponseCandidate() {
            return new ResponseCandidate(
                    id,
                    priority,
                    request -> requestMatch.headersPredicate().test(new ImpHeadersMatch(request.getRequestHeaders()))
                            && requestMatch.bodyPredicate().test(new ImpBodyMatch(request::getRequestBody))
                            && requestMatch.urlPredicate().test(new ImpUrlMatch(request.getRequestURI())),
                    () -> ImpResponse.builder()
                            .trustedStatus(responseStatus)
                            .bodyFromRequest(responseBodyFunction)
                            .trustedHeaders(responseHeadersOperator)
                            .build());
        }
    }

    public static final class AlwaysRespondSpecStart {

        public AlwaysRespondSpecBody withStatus(@Range(from = 100, to = 511) int status) {
            return new AlwaysRespondSpecBody(Preconditions.validHttpStatusCode(status));
        }
    }

    public static final class AlwaysRespondSpecBody {

        private final ImpHttpStatus responseStatus;

        AlwaysRespondSpecBody(ImpHttpStatus responseStatus) {
            this.responseStatus = responseStatus;
        }

        public AlwaysRespondSpecHeaders andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return defaultRespondHeaders(
                    ImpContentType.PLAIN_TEXT,
                    NamedFn.from(
                            "andTextBody",
                            ignored -> () -> new ByteArrayInputStream(textBody.getBytes(StandardCharsets.UTF_8))));
        }

        public AlwaysRespondSpecHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultRespondHeaders(
                    ImpContentType.JSON,
                    NamedFn.from(
                            "andJsonBody",
                            ignored -> () -> new ByteArrayInputStream(jsonBody.getBytes(StandardCharsets.UTF_8))));
        }

        public AlwaysRespondSpecHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultRespondHeaders(
                    ImpContentType.XML,
                    NamedFn.from(
                            "andXmlBody",
                            ignored -> () -> new ByteArrayInputStream(xmlBody.getBytes(StandardCharsets.UTF_8))));
        }

        public AlwaysRespondSpecHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultRespondHeaders(
                    ImpContentType.OCTET_STREAM, NamedFn.from("andDataStreamBody", ignored -> dataStreamSupplier));
        }

        public AlwaysRespondSpecHeaders andCustomContentTypeStream(
                String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultRespondHeaders(
                    contentType, NamedFn.from("andCustomContentTypeStream", ignored -> dataStreamSupplier));
        }

        private AlwaysRespondSpecHeaders defaultRespondHeaders(
                CharSequence contentType, NamedFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction) {
            return new AlwaysRespondSpecHeaders(responseStatus, contentType.toString(), bodyFunction);
        }
    }

    public static final class AlwaysRespondSpecHeaders {

        private final ImpHttpStatus status;
        private final String responseContentType;
        private final NamedFn<ImpRequestView, ImpSupplier<InputStream>> responseBodyFunction;

        AlwaysRespondSpecHeaders(
                ImpHttpStatus status,
                String responseContentType,
                NamedFn<ImpRequestView, ImpSupplier<InputStream>> responseBodyFunction) {
            this.status = status;
            this.responseContentType = responseContentType;
            this.responseBodyFunction = responseBodyFunction;
        }

        public AlwaysRespondSpecEnd andAdditionalHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new AlwaysRespondSpecEnd(status, responseBodyFunction, existingHeaders -> {
                var newHeaders = new Headers();
                newHeaders.putAll(headersCopy);
                newHeaders.put("Content-Type", List.of(responseContentType));
                newHeaders.putAll(existingHeaders);
                return Map.copyOf(newHeaders);
            });
        }

        public AlwaysRespondSpecEnd andExactHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new AlwaysRespondSpecEnd(status, responseBodyFunction, existingHeaders -> headersCopy);
        }

        public AlwaysRespondSpecEnd andNoAdditionalHeaders() {
            return new AlwaysRespondSpecEnd(status, responseBodyFunction, headers -> {
                var newHeaders = new Headers();
                newHeaders.putAll(headers);
                newHeaders.put("Content-Type", List.of(responseContentType));
                return Map.copyOf(newHeaders);
            });
        }
    }

    public static final class AlwaysRespondSpecEnd {

        private final ImpHttpStatus status;
        private final NamedFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction;
        private final ImpHeadersOperator headersOperator;

        AlwaysRespondSpecEnd(
                ImpHttpStatus status,
                NamedFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction,
                ImpHeadersOperator headersOperator) {
            this.status = status;
            this.bodyFunction = bodyFunction;
            this.headersOperator = headersOperator;
        }

        ResponseCandidate toResponseCandidate() {
            return new ResponseCandidate(ImpPredicate.alwaysTrue(), () -> ImpResponse.builder()
                    .trustedStatus(status)
                    .trustedBody(bodyFunction)
                    .trustedHeaders(headersOperator)
                    .build());
        }
    }

    public static final class RequestMatchingSpecContinue {

        private final List<ResponseCandidate> responseCandidates;

        RequestMatchingSpecContinue(List<ResponseCandidate> responseCandidates) {
            this.responseCandidates = responseCandidates;
        }

        public RequestMatchingSpecContinue matchRequest(ImpRequestMatch action) {
            Preconditions.nonNull(action, "action");
            var specEnd = action.apply(new RequestMatchingSpecStart());
            Preconditions.nonNull(specEnd, "matchRequest function result");
            var responseCandidate = specEnd.toResponseCandidate();
            return new RequestMatchingSpecContinue(
                    InternalUtils.addToNewListFinal(responseCandidates, responseCandidate));
        }

        public SpecFinal fallbackForNonMatching(
                ImpFn<ImpResponse.BuilderStatus, ImpResponse.BuilderHeaders> fallbackFn) {
            Preconditions.nonNull(fallbackFn, "fallbackFn");
            var impResponse = fallbackFn.apply(ImpResponse.builder()).build();
            return new SpecFinal(responseCandidates, candidates -> exchange -> impResponse);
        }

        public SpecFinal rejectNonMatching() {
            return new SpecFinal(responseCandidates, Teapot::new);
        }
    }

    public static final class SpecFinal {

        private final List<ResponseCandidate> responseCandidates;
        private final ImpFn<List<ResponseCandidate>, ImpFn<HttpExchange, ImpResponse>> fallback;

        SpecFinal(
                List<ResponseCandidate> responseCandidates,
                ImpFn<List<ResponseCandidate>, ImpFn<HttpExchange, ImpResponse>> fallback) {
            this.responseCandidates = responseCandidates;
            this.fallback = fallback;
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
                    .decision(new ResponseDecision(responseCandidates))
                    .fallback(fallback.apply(responseCandidates))
                    .build());
        }
    }
}
