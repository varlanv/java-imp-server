package com.varlanv.imp;

import com.sun.net.httpserver.HttpServer;

final class DefaultImpShared implements ImpShared {

    private final ServerConfig config;
    private final HttpServer httpServer;
    private final ImpStatistics statistics;

    DefaultImpShared(ServerConfig config, HttpServer httpServer, ImpStatistics statistics) {
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
        httpServer.stop(0);
    }

    @Override
    public ImpStatistics statistics() {
        return statistics;
    }
}
