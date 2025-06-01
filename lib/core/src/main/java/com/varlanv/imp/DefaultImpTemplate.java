package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

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
            var serverConfig = ImmutableStartedServerConfig.builder()
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

    private StartedServer buildAndStartServer(StartedServerConfig serverConfig, BorrowedState borrowedState) {
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
        ImpResponse impResponse;
        byte[] responseBytes;
        int responseStatus;
        try {
            var requestMethod = ImpMethod.of(exchange.getRequestMethod());
            if (requestMethod == null) {
                throw new IllegalStateException(String.format(
                        "Internal error in ImpServer - failed to parse HTTP method [ %s ] from request",
                        exchange.getRequestMethod()));
            }
            var impRequestView = new ImpRequestView(
                    requestMethod,
                    exchange.getRequestHeaders(),
                    () -> exchange.getRequestBody().readAllBytes(),
                    exchange.getRequestURI());
            var responseCandidate = serverConfig.decision().pick(impRequestView);
            if (responseCandidate == null) {
                serverContext.statistics().incrementMissCount();
                impResponse = serverConfig.fallback().apply(exchange);
                responseBytes = InternalUtils.readAllBytesFromSupplier(
                        impResponse.body().apply(impRequestView));
                responseStatus = impResponse.statusCode().value();
            } else {
                serverContext.statistics().incrementHitCount();
                impResponse = responseCandidate.responseSupplier().get();
                var trustedBodyFn = impResponse.trustedBody();
                try {
                    responseBytes = InternalUtils.readAllBytesFromSupplier(trustedBodyFn.apply(impRequestView));
                    responseStatus = impResponse.statusCode().value();
                } catch (Exception e) {
                    responseBytes = String.format(
                                    "Failed to read response body supplier, provided by `%s` method. "
                                            + "Message from exception thrown by provided supplier: %s",
                                    trustedBodyFn.name(), e.getMessage())
                            .getBytes(StandardCharsets.UTF_8);
                    responseStatus = 418;
                }
            }

            var originalResponseHeaders = exchange.getResponseHeaders();
            var newResponseHeaders = impResponse.headersOperator().apply(originalResponseHeaders);
            originalResponseHeaders.putAll(newResponseHeaders);
        } catch (Exception e) {
            responseBytes = Objects.requireNonNullElse(e.getMessage(), "").getBytes(StandardCharsets.UTF_8);
            responseStatus = 418;
        }
        if (responseStatus < 200) {
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.getResponseBody().close();
        } else {
            exchange.sendResponseHeaders(responseStatus, responseBytes.length);
            var responseBody = exchange.getResponseBody();
            responseBody.write(responseBytes);
            responseBody.flush();
            responseBody.close();
        }
    }

    ImpShared startShared() {
        var server = config.futureServer().createServer();
        var serverConfig = ImmutableStartedServerConfig.builder()
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
