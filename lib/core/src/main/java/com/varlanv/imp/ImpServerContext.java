package com.varlanv.imp;

final class ImpServerContext {

    private final StartedServerConfig config;
    private final MutableImpStatistics statistics;

    ImpServerContext(StartedServerConfig config, MutableImpStatistics statistics) {
        this.config = config;
        this.statistics = statistics;
    }

    public StartedServerConfig config() {
        return config;
    }

    public MutableImpStatistics statistics() {
        return statistics;
    }
}
