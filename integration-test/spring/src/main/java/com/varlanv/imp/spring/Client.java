package com.varlanv.imp.spring;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class Client {

    private final ClientProperties properties;
    private final RestClient restClient;

    public Client(ClientProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public ResponseEntity<String> get() {
        return restClient
                .get()
                .uri("http://localhost:" + properties.port(), String.class)
                .retrieve()
                .toEntity(String.class);
    }
}
