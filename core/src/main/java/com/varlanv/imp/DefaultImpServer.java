package com.varlanv.imp;

final class DefaultImpServer implements ImpServer {

    private final ServerConfig config;

    DefaultImpServer(ServerConfig config) {
        this.config = config;
    }

    @Override
    public int port() {
        return config.port().value();
    }
}
