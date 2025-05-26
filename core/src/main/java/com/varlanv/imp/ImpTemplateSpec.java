package com.varlanv.imp;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Range;

public final class ImpTemplateSpec {

    public static final class Start {

        Start() {}

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

        public RequestMatchingSpecMatch match(ImpMatchFn matchFunction) {
            Preconditions.nonNull(matchFunction, "matchFunction");
            var condition = matchFunction.apply(new ImpMatch());
            return new RequestMatchingSpecMatch(id, priority, condition);
        }
    }

    public static final class RequestMatchingSpecMatch {

        private final String id;
        private final int priority;
        private final ImpCondition condition;

        RequestMatchingSpecMatch(String id, int priority, ImpCondition condition) {
            this.id = id;
            this.priority = priority;
            this.condition = condition;
        }

        public RequestMatchingSpecStatus respondWithStatus(@Range(from = 100, to = 511) int status) {
            return new RequestMatchingSpecStatus(id, priority, condition, Preconditions.validHttpStatusCode(status));
        }
    }

    public static final class RequestMatchingSpecStatus {

        private final String id;
        private final int priority;
        private final ImpCondition condition;
        private final ImpHttpStatus responseStatus;

        RequestMatchingSpecStatus(String id, int priority, ImpCondition condition, ImpHttpStatus responseStatus) {
            this.id = id;
            this.priority = priority;
            this.condition = condition;
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
                    id, priority, condition, responseStatus, contentType.toString(), bodyFunction);
        }
    }

    public static final class RequestMatchingSpecHeaders {

        private final String id;
        private final int priority;
        private final ImpCondition condition;
        private final ImpHttpStatus responseStatus;
        private final String contentType;
        private final ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction;

        RequestMatchingSpecHeaders(
                String id,
                int priority,
                ImpCondition condition,
                ImpHttpStatus responseStatus,
                String contentType,
                ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFunction) {
            this.id = id;
            this.priority = priority;
            this.condition = condition;
            this.responseStatus = responseStatus;
            this.contentType = contentType;
            this.bodyFunction = bodyFunction;
        }

        public RequestMatchingSpecEnd andAdditionalHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInHeaders(headers, "headers");
            var headersCopy = Map.copyOf(headers);
            return new RequestMatchingSpecEnd(
                    id, priority, condition, responseStatus, bodyFunction, existingHeaders -> {
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
                    id, priority, condition, responseStatus, bodyFunction, existingHeaders -> headersCopy);
        }

        public RequestMatchingSpecEnd andNoAdditionalHeaders() {
            return new RequestMatchingSpecEnd(id, priority, condition, responseStatus, bodyFunction, headers -> {
                var newHeaders = new HashMap<>(headers);
                newHeaders.put("Content-Type", List.of(contentType));
                return Map.copyOf(newHeaders);
            });
        }
    }

    public static final class RequestMatchingSpecEnd {

        private final String id;
        private final int priority;
        private final ImpCondition condition;
        private final ImpHttpStatus responseStatus;
        private final ImpFn<ImpRequestView, ImpSupplier<InputStream>> responseBodyFunction;

        private final ImpHeadersOperator responseHeadersOperator;

        RequestMatchingSpecEnd(
                String id,
                int priority,
                ImpCondition condition,
                ImpHttpStatus responseStatus,
                ImpFn<ImpRequestView, ImpSupplier<InputStream>> responseBodyFunction,
                ImpHeadersOperator responseHeadersOperator) {
            this.id = id;
            this.priority = priority;
            this.condition = condition;
            this.responseStatus = responseStatus;
            this.responseBodyFunction = responseBodyFunction;
            this.responseHeadersOperator = responseHeadersOperator;
        }

        ResponseCandidate toResponseCandidate() {
            return new ResponseCandidate(id, priority, condition, () -> ImpResponse.builder()
                    .trustedStatus(responseStatus)
                    .bodyFromRequest(responseBodyFunction)
                    .trustedHeaders(responseHeadersOperator)
                    .build());
        }
    }

    public static final class AlwaysRespondSpecStart {

        AlwaysRespondSpecStart() {}

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
            return new ResponseCandidate(request -> true, () -> ImpResponse.builder()
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
            var newResponseCandidate = specEnd.toResponseCandidate();
            for (var existingResponseCandidate : responseCandidates) {
                if (existingResponseCandidate.id().equals(newResponseCandidate.id())) {
                    throw new IllegalArgumentException(String.format(
                            "Duplicated matcher id detected: [%s]. " + "Consider using unique id for each matcher. "
                                    + "Currently known matcher ids: %s",
                            newResponseCandidate.id(),
                            responseCandidates.stream()
                                    .map(ResponseCandidate::id)
                                    .collect(Collectors.toList())));
                } else if (existingResponseCandidate.priority() == newResponseCandidate.priority()) {
                    throw new IllegalArgumentException(String.format(
                            "Duplicated matcher priority detected: trying to set priority [%d] for matcher [%s], "
                                    + "but is already set for matcher [%s]. "
                                    + "Using same priority for different matchers can lead to unexpected, non-deterministic behavior. "
                                    + "Consider using unique priority for each matcher.",
                            newResponseCandidate.priority(),
                            newResponseCandidate.id(),
                            existingResponseCandidate.id()));
                }
            }
            return new RequestMatchingSpecContinue(
                    InternalUtils.addToNewListFinal(responseCandidates, newResponseCandidate));
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
