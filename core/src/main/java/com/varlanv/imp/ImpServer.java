package com.varlanv.imp;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class ImpServer {

    public static void main(final String[] args) throws Exception {
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(8080), 0);

            // 1. Define the context handler
            server.createContext("/", exchange -> {
                String responseText = "Hello :)";
                byte[] responseBytes = responseText.getBytes(StandardCharsets.UTF_8);

                // 2. Send response headers (status code 200 OK, and content length)
                exchange.sendResponseHeaders(200, responseBytes.length);

                // 3. Get the output stream and write the response body
                OutputStream responseBody = exchange.getResponseBody();
                responseBody.write(responseBytes);
                responseBody.flush();
                responseBody.close();
            });

            // 4. Start the server (after context is created)
            server.start();
            System.out.println("Server started on port 8080");

            // Give the server a moment to fully start (optional, but good for local quick tests)
            // Thread.sleep(100); // You might not need this after fixing the order and headers

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:8080/"))
                .GET()
                .build();

            System.out.println("Sending request to server...");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response status code: " + response.statusCode());
            System.out.println("Response body: " + response.body());

        } finally {
            if (server != null) {
                System.out.println("Stopping server...");
                server.stop(0); // 0 seconds delay
                System.out.println("Server stopped.");
            }
        }
    }
}
