package com.varlanv.imp;

final class DefaultImpServer implements ImpServer {

    private final ServerConfig config;
    private final MutableImpStatistics statistics;

    DefaultImpServer(ServerConfig config, MutableImpStatistics statistics) {
        this.config = config;
        this.statistics = statistics;
    }

    @Override
    public int port() {
        return config.port().value();
    }

    @Override
    public ImpStatistics statistics() {
        return new ImpStatistics(statistics);
    }
}
