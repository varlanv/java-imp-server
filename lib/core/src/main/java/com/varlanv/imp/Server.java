package com.varlanv.imp;

import com.sun.net.httpserver.HttpServer;

final class Server {

    private final int port;
    private final HttpServer actualServer;

    Server(int port, HttpServer actualServer) {
        this.port = port;
        this.actualServer = actualServer;
    }

    public int port() {
        return port;
    }

    public HttpServer actualServer() {
        return actualServer;
    }
}
