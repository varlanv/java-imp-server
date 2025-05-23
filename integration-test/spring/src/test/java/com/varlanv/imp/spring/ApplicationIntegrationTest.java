package com.varlanv.imp.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.varlanv.imp.ImpBorrowed;
import com.varlanv.imp.ImpServer;
import com.varlanv.imp.ImpShared;
import com.varlanv.imp.commontest.SlowTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationIntegrationTest implements SlowTest {

    private static final ImpShared sharedServer;
    private static final int defaultServerStatus = 200;
    private static final String defaultServerResponse = "Hello";

    @Autowired
    private Client client;

    static {
        sharedServer = ImpServer.template()
                .alwaysRespondWithStatus(defaultServerStatus)
                .andTextBody(defaultServerResponse)
                .andNoAdditionalHeaders()
                .startSharedOnRandomPort();
        System.setProperty("com.varlanv.imp.client.port", String.valueOf(sharedServer.port()));
    }

    @Test
    @DisplayName("should return default server response")
    void should_return_default_server_response() {
        var actual = client.get();

        assertThat(actual.getBody()).isEqualTo(defaultServerResponse);
        assertThat(actual.getStatusCode().value()).isEqualTo(200);
    }

    @Nested
    class CreatedFixture {

        private final ImpBorrowed borrowedServer = sharedServer
                .borrow()
                .alwaysRespondWithStatus(201)
                .andTextBody("Created")
                .andNoAdditionalHeaders();

        @Test
        @DisplayName("should return new response on borrowed server")
        void should_return_new_response_on_borrowed_server() {
            borrowedServer.useServer(server -> {
                var actual = client.get();

                assertThat(actual.getStatusCode().value()).isEqualTo(201);
                assertThat(actual.getBody()).isEqualTo("Created");
            });
        }
    }
}
