package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.HashSet;

final class DefaultImpTemplate implements ImpTemplate {

    private final ServerConfig config;

    DefaultImpTemplate(ServerConfig config) {
        this.config = config;
    }

    @Override
    public void useServer(ImpConsumer<ImpServer> consumer) {
        StartedServer server = null;
        try {
            var serverContext = new ImpServerContext(config, new MutableImpStatistics());
            server = buildAndStartServer(new BorrowedState(serverContext, false));
            consumer.accept(new DefaultImpServer(server.port(), serverContext));
        } catch (Exception e) {
            InternalUtils.hide(e);
        } finally {
            if (server != null) {
                server.dispose();
            }
        }
    }

    private StartedServer buildAndStartServer(BorrowedState borrowedState) {
        var server = config.futureServer().toServer();
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
        exchange.sendResponseHeaders(impResponse.statusCode().value(), responseBytes.length);
        var responseBody = exchange.getResponseBody();
        responseBody.write(responseBytes);
        responseBody.flush();
        responseBody.close();
    }

    ImpShared startShared() {
        var serverContext = new ImpServerContext(config, new MutableImpStatistics());
        var borrowedState = new BorrowedState(serverContext, true);
        var httpServer = buildAndStartServer(borrowedState);
        return new DefaultImpShared(serverContext, httpServer, borrowedState);
    }
}
