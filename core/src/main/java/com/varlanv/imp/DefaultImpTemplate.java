package com.varlanv.imp;

import com.sun.net.httpserver.HttpServer;

final class DefaultImpTemplate implements ImpTemplate {

    private final ServerConfig config;

    DefaultImpTemplate(ServerConfig config) {
        this.config = config;
    }

    @Override
    public void useServer(ImpConsumer<ImpServer> consumer) {
        HttpServer server = null;
        try {
            var impStatistics = new ImpStatistics();
            server = buildAndStartServer(impStatistics);
            consumer.accept(new DefaultImpServer(config, impStatistics));
        } catch (Exception e) {
            InternalUtils.hide(e);
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private HttpServer buildAndStartServer(ImpStatistics impStatistics) {
        var server = config.port().resolveToServer();
        server.createContext("/", exchange -> {
            var response = config.decision().pick(exchange);
            if (response == null) {
                impStatistics.incrementMissCount();
            } else {
                impStatistics.incrementHitCount();
                var impResponse = response.responseSupplier().get();
                var responseBytes = impResponse.body().get();
                var responseBody = exchange.getResponseBody();
                exchange.getResponseHeaders().putAll(impResponse.headers());
                exchange.sendResponseHeaders(impResponse.statusCode(), responseBytes.length);
                responseBody.write(responseBytes);
                responseBody.flush();
                responseBody.close();
            }
        });
        server.start();
        return server;
    }

    @Override
    public ImpShared startShared() {
        var impStatistics = new ImpStatistics();
        return new DefaultImpShared(config, buildAndStartServer(impStatistics), impStatistics);
    }
}
