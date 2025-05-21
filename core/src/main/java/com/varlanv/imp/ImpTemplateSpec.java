package com.varlanv.imp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

        OnRequestMatchingStatus onRequestMatching(ImpConsumer<ImmutableRequestMatch.Builder> specConsumer) {
            var builder = ImmutableRequestMatch.builder();
            Preconditions.nonNull(specConsumer, "consumer").accept(builder);
            return new OnRequestMatchingStatus(this, builder.build());
        }

        AlwaysRespond alwaysRespondWithStatus(@Range(from = 100, to = 511) int status) {
            return new AlwaysRespond(this, Preconditions.validHttpStatusCode(status));
        }
    }

    public static final class OnRequestMatchingStatus {

        private final ContentStart parent;
        private final RequestMatch requestMatch;

        OnRequestMatchingStatus(ContentStart contentStart, RequestMatch requestMatch) {
            this.parent = contentStart;
            this.requestMatch = requestMatch;
        }

        OnRequestMatchingBody respondWithStatus(@Range(from = 100, to = 511) int status) {
            return new OnRequestMatchingBody(this, requestMatch, Preconditions.validHttpStatusCode(status));
        }
    }

    public static final class OnRequestMatchingBody {

        private final OnRequestMatchingStatus parent;
        private final RequestMatch requestMatch;
        private final ImpHttpStatus status;

        OnRequestMatchingBody(OnRequestMatchingStatus parent, RequestMatch requestMatch, ImpHttpStatus status) {
            this.parent = parent;
            this.requestMatch = requestMatch;
            this.status = status;
        }

        OnRequestMatchingContentTypeHeaders andTextBody(String textBody) {
            Preconditions.nonNull(textBody, "textBody");
            return defaultImpTemplate(ImpContentType.PLAIN_TEXT, () -> textBody.getBytes(StandardCharsets.UTF_8));
        }

        OnRequestMatchingContentTypeHeaders andJsonBody(@Language("json") String jsonBody) {
            Preconditions.nonNull(jsonBody, "jsonBody");
            return defaultImpTemplate(ImpContentType.JSON, () -> jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        OnRequestMatchingContentTypeHeaders andXmlBody(@Language("xml") String xmlBody) {
            Preconditions.nonNull(xmlBody, "xmlBody");
            return defaultImpTemplate(ImpContentType.XML, () -> xmlBody.getBytes(StandardCharsets.UTF_8));
        }

        OnRequestMatchingContentTypeHeaders andDataStreamBody(ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    ImpContentType.OCTET_STREAM, () -> dataStreamSupplier.get().readAllBytes());
        }

        OnRequestMatchingContentTypeHeaders andCustomContentTypeStream(
                String contentType, ImpSupplier<InputStream> dataStreamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(dataStreamSupplier, "dataStreamSupplier");
            return defaultImpTemplate(
                    contentType, () -> dataStreamSupplier.get().readAllBytes());
        }

        private OnRequestMatchingContentTypeHeaders defaultImpTemplate(
                CharSequence contentType, ImpSupplier<byte[]> bodySupplier) {
            return new OnRequestMatchingContentTypeHeaders(this, () -> Map.entry(contentType.toString(), bodySupplier));
        }
    }

    public static final class OnRequestMatchingContentTypeHeaders {

        private final OnRequestMatchingBody parent;
        private final ImpSupplier<Map.Entry<String, ImpSupplier<byte[]>>> bodyAndContentTypeSupplier;

        public OnRequestMatchingContentTypeHeaders(
                OnRequestMatchingBody parent,
                ImpSupplier<Map.Entry<String, ImpSupplier<byte[]>>> bodyAndContentTypeSupplier) {
            this.parent = parent;
            this.bodyAndContentTypeSupplier = bodyAndContentTypeSupplier;
        }

        public ContentContinue andAdditionalHeaders(Map<String, List<String>> headers) {
            Preconditions.noNullsInMap(headers, "headers");
            return new ContentContinue();
        }

        public ContentContinue andNoAdditionalHeaders() {
            return new ContentContinue();
        }
    }

    public static final class ContentContinue {}

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
