package com.varlanv.imp.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.varlanv.imp.ImpServer;
import com.varlanv.imp.commontest.FastTest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class JaywayNotAvailableIntegrationTest implements FastTest {

    @Test
    @DisplayName("should fail immediately when jayway is not present on classpath")
    void should_fail_immediately_when_jayway_is_not_present_on_classpath() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ImpServer.httpTemplate()
                        .matchRequest(spec -> spec.id("anyId")
                                .priority(0)
                                .match(match -> match.jsonPath("$.key").stringEquals("val"))
                                .respondWithStatus(200)
                                .andTextBody("response body")
                                .andNoAdditionalHeaders())
                        .rejectNonMatching()
                        .onRandomPort())
                .withMessage(
                        "JsonPath library is not found on classpath. "
                                + "Library [ com.jayway.jsonpath:json-path ] is required on classpath to work with jsonPath matchers.");
    }

    @Test
    @DisplayName("should be able to handle non-jsonpath matchers when jsonpath is not available at classpath")
    void should_be_able_to_handle_non_jsonpath_matchers_when_jsonpath_is_not_available_at_classpath() {
        var requestBody = "some text";
        var responseBody = "response body";
        ImpServer.httpTemplate()
                .matchRequest(spec -> spec.id("anyId")
                        .priority(0)
                        .match(match -> match.body().bodyContains("text"))
                        .respondWithStatus(200)
                        .andTextBody(responseBody)
                        .andNoAdditionalHeaders())
                .rejectNonMatching()
                .onRandomPort()
                .useServer(impServer -> {
                    var response = sendHttpRequestWithBody(
                                    impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                            .join();

                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo(responseBody);
                    assertThat(response.headers().map())
                            .hasSize(3)
                            .containsOnlyKeys("content-type", "date", "content-length")
                            .containsEntry("content-type", List.of("text/plain"));
                });
    }
}
