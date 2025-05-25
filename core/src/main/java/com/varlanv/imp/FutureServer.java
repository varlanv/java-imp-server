package com.varlanv.imp;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;

final class FutureServer {

    private final PortSupplier portSupplier;

    FutureServer(PortSupplier portSupplier) {
        this.portSupplier = portSupplier;
    }

    Server createServer() {
        var retries = 5;
        RuntimeException exception = null;
        for (int iteration = 0; iteration < retries; iteration++) {
            try {
                var port = portSupplier.value();
                var server = HttpServer.create(new InetSocketAddress(port), 0);
                return new Server(port, server);
            } catch (BindException e) {
                if (iteration == retries - 1) {
                    if (portSupplier.isRandom()) {
                        exception = new IllegalStateException(
                                String.format("Could not acquire random port after [%d] retries", retries));
                    } else {
                        exception = new IllegalStateException(String.format(
                                "Could not acquire port [%d] after [%d] retries - port is in use",
                                portSupplier.value(), retries));
                    }
                }
            } catch (IOException e) {
                return InternalUtils.hide(e);
            }
        }
        if (exception == null) {
            throw new IllegalStateException("Unexpected exception - failed to start server");
        } else {
            throw exception;
        }
    }
}
