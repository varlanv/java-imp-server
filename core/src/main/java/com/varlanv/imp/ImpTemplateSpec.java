package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;
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

        public ContentStart randomPort() {
            return new ContentStart(new ImpPort(InternalUtils::randomPort, true));
        }

        public ContentStart port(int port) {
            return new ContentStart(new ImpPort(() -> port, false));
        }
    }

    public static final class ContentStart {

        private final ImpPort port;

        ContentStart(ImpPort port) {
            this.port = port;
        }

        public OnRequestMatchingStatus onRequestMatching(String id, ImpConsumer<RequestMatchBuilder> specConsumer) {
            Preconditions.nonNull(specConsumer, "consumer");
            Preconditions.nonBlank(id, "id");
            var builder = new RequestMatchBuilder();
            specConsumer.accept(builder);
            return new OnRequestMatchingStatus(id, this, builder.build());
        }

        public AlwaysRespond alwaysRespondWithStatus(@Range(from = 100, to = 511) int status) {
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

        public ImpTemplate fallbackForNonMatching(
                ImpFn<ImpResponse.BuilderStatus, ImpResponse.BuilderHeaders> builderFn) {
            var impResponse = builderFn.apply(ImpResponse.builder()).build();
            return buildTemplate(candidates -> exchange -> impResponse);
        }

        public ImpTemplate rejectNonMatching() {
            return buildTemplate(Teapot::new);
        }

        private ImpTemplate buildTemplate(ImpFn<List<ResponseCandidate>, ImpFn<HttpExchange, ImpResponse>> fallback) {
            var candidates = List.of(new ResponseCandidate(
                    parent.parent.parent.matchId,
                    request -> parent.parent
                            .parent
                            .requestMatch
                            .headersPredicate()
                            .test(new ImpHeadersMatch(request.getRequestHeaders())),
                    () -> ImpResponse.builder()
                            .trustedStatus(parent.parent.status)
                            .body(parent.bodySupplier)
                            .trustedHeaders(responseHeadersOperator)
                            .build()));
            return new DefaultImpTemplate(ImmutableServerConfig.builder()
                    .port(parent.parent.parent.parent.port)
                    .decision(new ResponseDecision(candidates))
                    .fallback(fallback.apply(candidates))
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

        public ImpTemplate andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return defaultImpTemplate(ImpContentType.PLAIN_TEXT, () -> textBody.getBytes(StandardCharsets.UTF_8));
        }

        public ImpTemplate andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultImpTemplate(ImpContentType.JSON, () -> jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        public ImpTemplate andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultImpTemplate(ImpContentType.XML, () -> xmlBody.getBytes(StandardCharsets.UTF_8));
        }

        public ImpTemplate andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    ImpContentType.OCTET_STREAM, () -> dataStreamSupplier.get().readAllBytes());
        }

        public ImpTemplate andCustomContentTypeStream(String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    contentType, () -> dataStreamSupplier.get().readAllBytes());
        }

        private DefaultImpTemplate defaultImpTemplate(CharSequence contentType, ImpSupplier<byte[]> bodySupplier) {
            return new DefaultImpTemplate(ImmutableServerConfig.builder()
                    .port(parent.port)
                    .decision(new ResponseDecision(
                            new ResponseCandidate(ImpPredicate.alwaysTrue(), () -> ImpResponse.builder()
                                    .trustedStatus(status)
                                    .body(bodySupplier)
                                    .trustedHeaders(headers -> {
                                        var res = new HashMap<>(headers);
                                        res.put("Content-Type", List.of(contentType.toString()));
                                        return Collections.unmodifiableMap(res);
                                    })
                                    .build())))
                    .fallback(new Teapot(List.of()))
                    .build());
        }
    }
}
