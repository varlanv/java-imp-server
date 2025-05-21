package com.varlanv.imp;

import com.sun.net.httpserver.Headers;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

final class DefaultImpTemplate implements ImpTemplate {

    private final ServerConfig config;

    DefaultImpTemplate(ServerConfig config) {
        this.config = config;
    }

    @Override
    public void useServer(ImpConsumer<ImpServer> consumer) {
        Disposable server = null;
        try {
            var impStatistics = new MutableImpStatistics();
            server = buildAndStartServer(impStatistics);
            consumer.accept(new DefaultImpServer(config, impStatistics));
        } catch (Exception e) {
            InternalUtils.hide(e);
        } finally {
            if (server != null) {
                server.dispose();
            }
        }
    }

    private Disposable buildAndStartServer(MutableImpStatistics impStatistics) {
        var server = config.port().resolveToServer();
        server.createContext("/", exchange -> {
            var response = config.decision().pick(exchange);
            ImpResponse impResponse;
            if (response == null) {
                impStatistics.incrementMissCount();
                impResponse = config.fallback().apply(exchange);
            } else {
                impStatistics.incrementHitCount();
                impResponse = response.responseSupplier().get();
            }
            var responseBytes = impResponse.body().get();
            var responseBody = exchange.getResponseBody();
            var originalResponseHeaders = exchange.getResponseHeaders();
            var newResponseHeaders = impResponse.headers().apply(originalResponseHeaders);
            for (var entry : new HashSet<>(originalResponseHeaders.entrySet())) {
                if (!newResponseHeaders.containsKey(entry.getKey())) {
                    originalResponseHeaders.remove(entry.getKey());
                }
            }
            originalResponseHeaders.putAll(newResponseHeaders);
            exchange.sendResponseHeaders(impResponse.statusCode().value(), responseBytes.length);
            responseBody.write(responseBytes);
            responseBody.flush();
            responseBody.close();
        });
        server.start();
        return () -> server.stop(0);
    }

    @Override
    public ImpShared startShared() {
        var impStatistics = new MutableImpStatistics();
        return new DefaultImpShared(config, buildAndStartServer(impStatistics), impStatistics);
    }
}
