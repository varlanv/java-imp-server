package com.varlanv.imp;

import java.util.function.IntSupplier;

public final class ImpTemplateSpec {

    public static final class Start {

        Content randomPort() {
            return new Content(InternalUtils::randomPort);
        }

        Content port(int port) {
            return new Content(() -> port);
        }
    }

    public static final class Content {

        private final IntSupplier portSupplier;

        Content(IntSupplier portSupplier) {
            this.portSupplier = portSupplier;
        }

        End alwaysRespondWith() {
            return new End(this);
        }
    }

    public static final class End {

        private final Content content;

        End(Content content) {
            this.content = content;
        }

        ImpTemplate template() {
            return new DefaultImpTemplate();
        }
    }
}
