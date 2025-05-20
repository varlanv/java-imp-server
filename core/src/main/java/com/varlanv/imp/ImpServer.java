package com.varlanv.imp;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public interface ImpServer {

    static ImpTemplateSpec.Start template() {
        return new ImpTemplateSpec.Start();
    }

    private static void consumeServer(ImpConsumer<HttpServer> consumer) {
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                var responseText = "Hello :)";
                var responseBytes = responseText.getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(200, responseBytes.length);

                var responseBody = exchange.getResponseBody();
                responseBody.write(responseBytes);
                responseBody.flush();
                responseBody.close();
            });
            server.start();

            consumer.accept(server);
        } catch (Exception e) {
            InternalUtils.hide(e);
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }
}
