package com.varlanv.imp;

public final class ImpBorrowed {

    private final ServerConfig config;
    private final DefaultImpShared parent;

    ImpBorrowed(ServerConfig config, DefaultImpShared parent) {
        this.config = config;
        this.parent = parent;
    }

    public ImpStatistics useServer(ImpConsumer<ImpServer> consumer) {
        return parent.useWithMutatedContext(config, consumer);
    }
}
