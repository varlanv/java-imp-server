package com.varlanv.imp;

import com.sun.net.httpserver.HttpServer;

final class StartedServer {

    private final int port;
    private final HttpServer httpServer;

    StartedServer(int port, HttpServer httpServer) {
        this.port = port;
        this.httpServer = httpServer;
    }

    public int port() {
        return port;
    }

    public void dispose() {
        httpServer.stop(0);
    }
}
