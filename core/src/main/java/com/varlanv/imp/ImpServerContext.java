package com.varlanv.imp;

final class ImpServerContext {

    private final ServerConfig config;
    private final MutableImpStatistics statistics;

    ImpServerContext(ServerConfig config, MutableImpStatistics statistics) {
        this.config = config;
        this.statistics = statistics;
    }

    public ServerConfig config() {
        return config;
    }

    public MutableImpStatistics statistics() {
        return statistics;
    }
}
