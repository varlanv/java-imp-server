package com.varlanv.imp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Range;

public final class ImpTemplateSpec {

    public static final class Start {

        Content randomPort() {
            return new Content(new ImpPort(InternalUtils::randomPort, true));
        }

        Content port(int port) {
            return new Content(new ImpPort(() -> port, false));
        }
    }

    public static final class Content {

        private final ImpPort port;

        Content(ImpPort port) {
            this.port = port;
        }

        AlwaysRespond alwaysRespondWithStatus(@Range(from = 100, to = 511) int status) {
            var httpStatus = ImpHttpStatus.forCode(status);
            if (httpStatus == null) {
                throw new IllegalArgumentException(String.format("Invalid http status code [%d]", status));
            }
            return new AlwaysRespond(this, httpStatus);
        }
    }

    public static final class AlwaysRespond {

        private final Content parent;
        private final ImpHttpStatus status;

        AlwaysRespond(Content parent, ImpHttpStatus status) {
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

        ImpTemplate andDataStreamBody(ImpSupplier<InputStream> streamSupplier) {
            Preconditions.nonNull(streamSupplier, "streamSupplier");
            return defaultImpTemplate(
                    ImpContentType.OCTET_STREAM, () -> streamSupplier.get().readAllBytes());
        }

        ImpTemplate andCustomContentType(String contentType, ImpSupplier<InputStream> streamSupplier) {
            Preconditions.nonBlank(contentType, "contentType");
            Preconditions.nonNull(streamSupplier, "streamSupplier");
            return defaultImpTemplate(contentType, () -> streamSupplier.get().readAllBytes());
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
