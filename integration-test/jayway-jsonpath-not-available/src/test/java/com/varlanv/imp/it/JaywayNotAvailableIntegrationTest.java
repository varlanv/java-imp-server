package com.varlanv.imp.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.varlanv.imp.ImpServer;
import com.varlanv.imp.commontest.FastTest;
import java.net.http.HttpResponse;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class JaywayNotAvailableIntegrationTest implements FastTest {

    @Test
    @DisplayName("should fail with message when jayway is not present on classpath")
    void should_fail_with_message_when_jayway_is_not_present_on_classpath() {
        @Language("json")
        var requestBody = """
        {
          "key": "val"
        }
        """;
        var subject = ImpServer.httpTemplate()
                .onRequestMatching(
                        "any",
                        request ->
                                request.bodyPredicate(b -> b.jsonPath("$.key").stringEquals("val")))
                .respondWithStatus(200)
                .andTextBody("response body")
                .andNoAdditionalHeaders()
                .rejectNonMatching()
                .onRandomPort();

        subject.useServer(impServer -> {
            var response = sendHttpRequestWithBody(impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                    .join();
            var responseHeaders = response.headers().map();
            assertThat(response.statusCode()).isEqualTo(418);
            assertThat(response.body())
                    .isEqualTo(
                            "Exception was thrown by request predicate with id [any]. "
                                    + "Please check your ImpServer configuration for [any] request matcher. "
                                    + "Thrown error is [java.lang.IllegalStateException]: JsonPath library is not found on classpath. "
                                    + "Library [ com.jayway.jsonpath:json-path ] is required on classpath to work with jsonPath matchers.");
            assertThat(responseHeaders).hasSize(2);
        });
    }
}
