package com.varlanv.imp;

public final class ImpBorrowed {

    private final ServerConfig config;
    private final DefaultImpShared parent;

    ImpBorrowed(ServerConfig config, DefaultImpShared parent) {
        this.config = config;
        this.parent = parent;
    }

    public ImpStatistics useServer(ImpConsumer<ImpServer> consumer) {
        if (parent.isDisposed()) {
            throw new IllegalStateException("Shared server is already stopped. Cannot use borrowed server anymore.");
        }
        var counter = parent.inProgressRequestCounter().getAsInt();
        if (counter > 0) {
            throw new IllegalStateException(String.format(
                    "Concurrent usage of borrowed server detected. "
                            + "It is expected that during borrowing, only code inside `useServer` "
                            + "lambda will interact with the server, but before entering `useServer` lambda, "
                            + "there was %d in-progress requests running on server. Consider synchronizing access to "
                            + "server before entering `useServer` lambda, or use non-shared server instead.",
                    counter));
        }
        return parent.useWithMutatedContext(config, consumer);
    }
}
