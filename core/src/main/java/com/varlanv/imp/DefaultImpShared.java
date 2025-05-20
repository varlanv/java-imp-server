package com.varlanv.imp;

import com.sun.net.httpserver.HttpServer;

final class DefaultImpShared implements ImpShared {

    private final ServerConfig config;
    private final HttpServer httpServer;

    DefaultImpShared(ServerConfig config, HttpServer httpServer) {
        this.config = config;
        this.httpServer = httpServer;
    }

    @Override
    public int port() {
        return config.port().value();
    }

    @Override
    public void dispose() {
        httpServer.stop(0);
    }
}
