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
            var decision = config.decision();
            server = config.port().resolveToServer();
            server.createContext("/", exchange -> {
                var response = decision.pick(exchange);
                if (response != null) {
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

            consumer.accept(new DefaultImpServer(config));
        } catch (Exception e) {
            InternalUtils.hide(e);
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    @Override
    public ImpShared startShared() {
        return new DefaultImpShared();
    }
}
