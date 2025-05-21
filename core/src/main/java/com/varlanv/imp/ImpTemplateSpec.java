package com.varlanv.imp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.intellij.lang.annotations.Language;

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

        AlwaysRespond alwaysRespondWithStatus(int status) {
            return new AlwaysRespond(this, status);
        }
    }

    public static final class AlwaysRespond {

        private final Content parent;
        private final int status;

        AlwaysRespond(Content parent, int status) {
            this.parent = parent;
            this.status = status;
        }

        ImpTemplate andTextBody(String response) {
            Objects.requireNonNull(response);
            return defaultImpTemplate(ImpContentType.TEXT_PLAIN, () -> response.getBytes(StandardCharsets.UTF_8));
        }

        ImpTemplate andJsonBody(@Language("json") String response) {
            Objects.requireNonNull(response);
            return defaultImpTemplate(ImpContentType.APPLICATION_JSON, () -> response.getBytes(StandardCharsets.UTF_8));
        }

        ImpTemplate andXmlBody(@Language("xml") String response) {
            Objects.requireNonNull(response);
            return defaultImpTemplate(ImpContentType.APPLICATION_XML, () -> response.getBytes(StandardCharsets.UTF_8));
        }

        ImpTemplate andDataStreamBody(ImpSupplier<InputStream> streamSupplier) {
            Objects.requireNonNull(streamSupplier);
            return defaultImpTemplate(
                    ImpContentType.OCTET_STREAM, () -> streamSupplier.get().readAllBytes());
        }

        private DefaultImpTemplate defaultImpTemplate(ImpContentType contentType, ImpSupplier<byte[]> bodySupplier) {
            return new DefaultImpTemplate(ImmutableServerConfig.builder()
                    .port(parent.port)
                    .decision(new ResponseDecision(
                            new ResponseCandidate(ImpPredicate.alwaysTrue(), () -> ImmutableImpResponse.builder()
                                    .body(bodySupplier)
                                    .headers(Map.of("Content-Type", List.of(contentType.stringValue())))
                                    .statusCode(status)
                                    .build())))
                    .build());
        }
    }
}
