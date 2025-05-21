package com.varlanv.imp;

final class DefaultImpShared implements ImpShared {

    private final ServerConfig config;
    private final Disposable httpServer;
    private final MutableImpStatistics statistics;

    DefaultImpShared(ServerConfig config, Disposable httpServer, MutableImpStatistics statistics) {
        this.config = config;
        this.httpServer = httpServer;
        this.statistics = statistics;
    }

    @Override
    public int port() {
        return config.port().value();
    }

    @Override
    public void dispose() {
        httpServer.dispose();
    }

    @Override
    public ImpStatistics statistics() {
        return new ImpStatistics(statistics);
    }
}
