package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.HashSet;

final class DefaultImpTemplate implements ImpTemplate {

    private final TemplateConfig config;

    DefaultImpTemplate(TemplateConfig config) {
        this.config = config;
    }

    @Override
    public void useServer(ImpConsumer<ImpServer> consumer) {
        StartedServer startedServer = null;
        try {
            var server = config.futureServer().createServer();
            var serverConfig = ImmutableServerConfig.builder()
                    .server(server)
                    .decision(config.decision())
                    .fallback(config.fallback())
                    .build();
            var serverContext = new ImpServerContext(serverConfig, new MutableImpStatistics());
            startedServer = buildAndStartServer(serverConfig, new BorrowedState(serverContext, false));
            consumer.accept(new DefaultImpServer(startedServer.port(), serverContext));
        } catch (Exception e) {
            InternalUtils.hide(e);
        } finally {
            if (startedServer != null) {
                startedServer.dispose();
            }
        }
    }

    private StartedServer buildAndStartServer(ServerConfig serverConfig, BorrowedState borrowedState) {
        var server = serverConfig.server();
        var httpServer = server.actualServer();
        httpServer.createContext("/", exchange -> {
            if (borrowedState.isShared()) {
                var counter = borrowedState.inProgressRequestCounter();
                try {
                    counter.incrementAndGet();
                    process(borrowedState.currentContext(), exchange);
                } finally {
                    counter.decrementAndGet();
                }
            } else {
                process(borrowedState.currentContext(), exchange);
            }
        });
        httpServer.start();
        return new StartedServer(server.port(), httpServer);
    }

    private void process(ImpServerContext serverContext, HttpExchange exchange) throws IOException {
        var serverConfig = serverContext.config();
        var response = serverConfig.decision().pick(exchange);
        ImpResponse impResponse;
        if (response == null) {
            serverContext.statistics().incrementMissCount();
            impResponse = serverConfig.fallback().apply(exchange);
        } else {
            serverContext.statistics().incrementHitCount();
            impResponse = response.responseSupplier().get();
        }
        var responseBytes = impResponse.body().get();
        var originalResponseHeaders = exchange.getResponseHeaders();
        var newResponseHeaders = impResponse.headersOperator().apply(originalResponseHeaders);
        if (!originalResponseHeaders.isEmpty()) {
            for (var entry : new HashSet<>(originalResponseHeaders.entrySet())) {
                if (!newResponseHeaders.containsKey(entry.getKey())) {
                    originalResponseHeaders.remove(entry.getKey());
                }
            }
        }
        originalResponseHeaders.putAll(newResponseHeaders);
        var expectedStatus = impResponse.statusCode().value();
        if (expectedStatus < 200) {
            exchange.sendResponseHeaders(expectedStatus, 0);
            exchange.getResponseBody().close();
        } else {
            exchange.sendResponseHeaders(expectedStatus, responseBytes.length);
            var responseBody = exchange.getResponseBody();
            responseBody.write(responseBytes);
            responseBody.flush();
            responseBody.close();
        }
    }

    ImpShared startShared() {
        var server = config.futureServer().createServer();
        var serverConfig = ImmutableServerConfig.builder()
                .server(server)
                .decision(config.decision())
                .fallback(config.fallback())
                .build();
        var serverContext = new ImpServerContext(serverConfig, new MutableImpStatistics());
        var borrowedState = new BorrowedState(serverContext, true);
        var httpServer = buildAndStartServer(serverConfig, borrowedState);
        return new DefaultImpShared(serverContext, httpServer, borrowedState);
    }
}
