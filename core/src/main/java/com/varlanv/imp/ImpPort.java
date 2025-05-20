package com.varlanv.imp;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Map;

final class ImpPort {

    private final MemoizedSupplier<Map.Entry<HttpServer, Integer>> resolvedServerSupplier;

    ImpPort(ImpSupplier<Integer> portSupplier, boolean isRandom) {
        this.resolvedServerSupplier = MemoizedSupplier.of(() -> resolve(portSupplier, isRandom));
    }

    public int value() {
        if (!resolvedServerSupplier.isInitialized()) {
            throw new IllegalStateException("Port has not been resolved yet");
        }
        return resolvedServerSupplier.get().getValue();
    }

    public HttpServer resolveToServer() {
        return resolvedServerSupplier.get().getKey();
    }

    private static Map.Entry<HttpServer, Integer> resolve(ImpSupplier<Integer> portSupplier, boolean isRandom) {
        var retries = 5;
        for (int iteration = 0; iteration < retries; iteration++) {
            try {
                var port = portSupplier.get();
                var server = HttpServer.create(new InetSocketAddress(port), 0);
                return Map.entry(server, port);
            } catch (BindException e) {
                if (iteration == retries - 1) {
                    return InternalUtils.hide(e);
                }
            } catch (IOException e) {
                return InternalUtils.hide(e);
            }
        }
        if (isRandom) {
            throw new IllegalStateException(String.format("Could not acquire random port after [%d] retries", retries));
        } else {
            throw new IllegalStateException(String.format(
                    "Could not acquire port [%d] after [%d] retries - port is in use", portSupplier.get(), retries));
        }
    }
}
