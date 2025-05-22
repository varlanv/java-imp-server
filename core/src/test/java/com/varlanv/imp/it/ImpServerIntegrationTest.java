package com.varlanv.imp.it;

import static org.assertj.core.api.Assertions.*;

import com.varlanv.imp.*;
import com.varlanv.imp.commontest.BaseTest;
import com.varlanv.imp.commontest.FastTest;
import java.io.ByteArrayInputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

public class ImpServerIntegrationTest implements FastTest {

    @RepeatedTest(100)
    @DisplayName("Should be able to start server with random port")
    void should_be_able_to_start_server_with_random_port() {
        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody("")
                .useServer(impServer -> {});
    }

    @Test
    @DisplayName("Fresh server should have zero hits and misses")
    void fresh_server_should_have_zero_hits_and_misses() {
        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody("")
                .useServer(impServer -> {
                    assertThat(impServer.statistics().hitCount()).isZero();
                    assertThat(impServer.statistics().missCount()).isZero();
                });
    }

    @Test
    @DisplayName("When server has one hit, should add it to statistics")
    void when_server_has_one_hit_should_add_it_to_statistics() {
        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody("somePort")
                .useServer(impServer -> {
                    var request = HttpRequest.newBuilder(
                                    new URI(String.format("http://localhost:%d/", impServer.port())))
                            .build();
                    sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(impServer.statistics().hitCount()).isOne();
                    assertThat(impServer.statistics().missCount()).isZero();
                });
    }

    @Test
    @DisplayName(
            "When server has zero hits, then read statistics, then make hit - then should not modify original statistic")
    void when_server_has_zero_hits_then_read_statistics_then_make_hit_then_should_not_modify_original_statistic() {
        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody("somePort")
                .useServer(impServer -> {
                    var statistics = impServer.statistics();
                    var request = HttpRequest.newBuilder(
                                    new URI(String.format("http://localhost:%d/", impServer.port())))
                            .build();
                    sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(statistics.hitCount()).isZero();
                    assertThat(statistics.missCount()).isZero();
                });
    }

    @Test
    @DisplayName(
            "When server has one hit, then read statistics, then make another hit - then should not modify original statistic")
    void
            when_server_has_one_hit_then_read_statistics_then_make_another_hit_then_should_not_modify_original_statistic() {
        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody("somePort")
                .useServer(impServer -> {
                    var request = HttpRequest.newBuilder(
                                    new URI(String.format("http://localhost:%d/", impServer.port())))
                            .build();
                    sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

                    var statistics = impServer.statistics();
                    assertThat(statistics.hitCount()).isOne();
                    assertThat(statistics.missCount()).isZero();

                    sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());
                    assertThat(statistics.hitCount()).isOne();
                    assertThat(statistics.missCount()).isZero();
                });
    }

    @Test
    @DisplayName("When server has many hits, should add all to statistics")
    void when_server_has_many_hits_should_add_all_to_statistics() {
        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody("somePort")
                .useServer(impServer -> {
                    var count = 50;
                    for (var i = 0; i < count; i++) {
                        var request = HttpRequest.newBuilder(
                                        new URI(String.format("http://localhost:%d/", impServer.port())))
                                .build();
                        sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());
                    }

                    assertThat(impServer.statistics().hitCount()).isEqualTo(count);
                    assertThat(impServer.statistics().missCount()).isZero();
                });
    }

    @Nested
    @Isolated
    class ConcurrencyTest {

        @Test
        @DisplayName("When sanding many requests in parallel, should count all statistic")
        void when_sanding_many_requests_in_parallel_should_count_all_statistic() {
            ImpServer.template()
                    .randomPort()
                    .alwaysRespondWithStatus(200)
                    .andTextBody("somePort")
                    .useServer(impServer -> {
                        var count = 50;
                        var latchCounter = new CountDownLatch(count);
                        var allReadyLatch = new CountDownLatch(1);
                        @SuppressWarnings("resource")
                        var executorService = Executors.newFixedThreadPool(count);
                        try {
                            for (var i = 0; i < count; i++) {
                                executorService.submit(() -> {
                                    try {
                                        latchCounter.countDown();
                                        allReadyLatch.await();
                                        var request = HttpRequest.newBuilder(new URI(
                                                        String.format("http://localhost:%d/", impServer.port())))
                                                .build();
                                        sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());
                                    } catch (Exception e) {
                                        BaseTest.hide(e);
                                    }
                                });
                            }
                            if (!latchCounter.await(5, TimeUnit.SECONDS)) {
                                throw new TimeoutException("Failed to start fixture");
                            }
                            allReadyLatch.countDown();
                            executorService.shutdown();
                            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                                throw new TimeoutException("Failed to execute fixture");
                            }
                            assertThat(impServer.statistics().hitCount()).isEqualTo(count);
                            assertThat(impServer.statistics().missCount()).isZero();
                        } finally {
                            executorService.shutdownNow();
                        }
                    });
        }
    }

    @ParameterizedTest
    @ArgumentsSource(HttpRequestBuilderSource.class)
    @DisplayName("server should response with expected json data")
    void server_should_response_with_expected_json_data(
            Function<Integer, HttpRequest.Builder> httpRequestBuilderSupplier) {
        @Language("json")
        var expected = """
            {
              "key": "val"
            }
            """;

        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andJsonBody(expected)
                .useServer(impServer -> {
                    var request =
                            httpRequestBuilderSupplier.apply(impServer.port()).build();
                    var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(expected);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers).containsEntry("Content-Type", List.of("application/json"));
                        assertThat(headers).containsEntry("Content-Length", List.of("19"));
                        assertThat(headers).containsKey("date");
                    });
                });
    }

    @ParameterizedTest
    @ArgumentsSource(HttpRequestBuilderSource.class)
    @DisplayName("server should response with expected custom content type")
    void server_should_response_with_expected_custom_content_type(
            Function<Integer, HttpRequest.Builder> httpRequestBuilderSupplier) {
        var expected = "some text";
        var contentType = "some/content/type";

        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andCustomContentTypeStream(
                        contentType, () -> new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8)))
                .useServer(impServer -> {
                    var request =
                            httpRequestBuilderSupplier.apply(impServer.port()).build();
                    var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(expected);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers).containsEntry("Content-Type", List.of(contentType));
                        assertThat(headers).containsEntry("Content-Length", List.of(String.valueOf(expected.length())));
                        assertThat(headers).containsKey("date");
                    });
                });
    }

    @ParameterizedTest
    @ArgumentsSource(HttpRequestBuilderSource.class)
    @DisplayName("server should response with expected text data")
    void server_should_response_with_expected_text_data(
            Function<Integer, HttpRequest.Builder> httpRequestBuilderSupplier) {
        var expected = "some text";

        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody(expected)
                .useServer(impServer -> {
                    var request =
                            httpRequestBuilderSupplier.apply(impServer.port()).build();
                    var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(expected);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers).containsEntry("Content-Type", List.of("text/plain"));
                        assertThat(headers).containsEntry("Content-Length", List.of("9"));
                        assertThat(headers).containsKey("date");
                    });
                });
    }

    @ParameterizedTest
    @ArgumentsSource(HttpRequestBuilderSource.class)
    @DisplayName("server should response with expected stream data")
    void server_should_response_with_expected_stream_data(
            Function<Integer, HttpRequest.Builder> httpRequestBuilderSupplier) {
        var expected = "some text";

        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andDataStreamBody(() -> new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8)))
                .useServer(impServer -> {
                    var request =
                            httpRequestBuilderSupplier.apply(impServer.port()).build();
                    var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(expected);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers).containsEntry("Content-Type", List.of("application/octet-stream"));
                        assertThat(headers).containsEntry("Content-Length", List.of(String.valueOf(expected.length())));
                        assertThat(headers).containsKey("date");
                    });
                });
    }

    @ParameterizedTest
    @ArgumentsSource(HttpRequestBuilderSource.class)
    @DisplayName("server should response with expected xml data")
    void server_should_response_with_expected_xml_data(
            Function<Integer, HttpRequest.Builder> httpRequestBuilderSupplier) {
        @Language("xml")
        var expected =
                """
                <root>
                <child>text</child>
                </root>
                """;

        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andXmlBody(expected)
                .useServer(impServer -> {
                    var request =
                            httpRequestBuilderSupplier.apply(impServer.port()).build();
                    var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(expected);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers).containsEntry("Content-Type", List.of("application/xml"));
                        assertThat(headers).containsEntry("Content-Length", List.of(String.valueOf(expected.length())));
                        assertThat(headers).containsKey("date");
                    });
                });
    }

    @Test
    @DisplayName("should be able to start server at specific port")
    void should_be_able_to_start_server_at_specific_port() {
        var port = randomPort();
        var someText = "some text";
        ImpServer.template()
                .port(port)
                .alwaysRespondWithStatus(200)
                .andTextBody(someText)
                .useServer(impServer -> {
                    var request = HttpRequest.newBuilder(
                                    new URI(String.format("http://localhost:%d/", impServer.port())))
                            .build();
                    var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(someText);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers).containsEntry("Content-Type", List.of("text/plain"));
                        assertThat(headers).containsEntry("Content-Length", List.of(String.valueOf(someText.length())));
                        assertThat(headers).containsKey("date");
                    });
                });
    }

    @Test
    @DisplayName("should throw exception when requested port is already in use")
    void should_throw_exception_when_requested_port_is_already_in_use() throws Exception {
        var port = randomPort();
        var sleepDuration = Duration.ofMillis(500);
        var startedLatch = new CountDownLatch(1);
        new Thread(() -> ImpServer.template()
                        .port(port)
                        .alwaysRespondWithStatus(200)
                        .andTextBody("some text")
                        .useServer(impServer -> {
                            startedLatch.countDown();
                            Thread.sleep(sleepDuration);
                        }))
                .start();

        if (!startedLatch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Server not started");
        }

        assertThatThrownBy(() -> ImpServer.template()
                        .port(port)
                        .alwaysRespondWithStatus(200)
                        .andTextBody("some text")
                        .useServer(impServer -> {}))
                .isInstanceOf(BindException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    @DisplayName("should be able to send multiple requests to shared server")
    void should_be_able_to_send_multiple_requests_to_shared_server() {
        var body = "some text";
        var sharedServer = ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody(body)
                .startShared();

        ImpRunnable action = () -> {
            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", sharedServer.port())))
                    .build();
            var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.body()).isEqualTo(body);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                assertThat(headers).containsEntry("Content-Type", List.of("text/plain"));
                assertThat(headers).containsEntry("Content-Length", List.of(String.valueOf(body.length())));
                assertThat(headers).containsKey("date");
            });
        };

        try {
            action.run();
            action.run();
            action.run();
        } finally {
            sharedServer.dispose();
        }
    }

    @Test
    @DisplayName("should be able to send request while borrowing server")
    void should_be_able_to_send_request_while_borrowing_server() {
        var body = "some text";
        var sharedServer = ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody(body)
                .startShared();

        try {
            var borrowedTextBody = "some borrowed text";
            var borrowedStatus = 400;
            var impBorrowed = sharedServer
                    .borrow()
                    .alwaysRespondWithStatus(borrowedStatus)
                    .andTextBody(borrowedTextBody)
                    .andNoAdditionalHeaders();

            impBorrowed.useServer(server -> {
                var response = sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.body()).isEqualTo(borrowedTextBody);
                assertThat(response.statusCode()).isEqualTo(borrowedStatus);
            });
        } finally {
            sharedServer.dispose();
        }
    }

    @Test
    @DisplayName("borrowed server port should be same as original server port")
    void borrowed_server_port_should_be_same_as_original_server_port() {
        var sharedServer = ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody("any")
                .startShared();

        try {
            var impBorrowed = sharedServer
                    .borrow()
                    .alwaysRespondWithStatus(400)
                    .andTextBody("anyBorrowed")
                    .andNoAdditionalHeaders();

            impBorrowed.useServer(server -> {
                assertThat(server.port()).isEqualTo(sharedServer.port());
            });
        } finally {
            sharedServer.dispose();
        }
    }

    @Test
    @DisplayName("should return to original state after borrowing closure ends")
    void should_return_to_original_state_after_borrowing_closure_ends() {
        var originalBody = "some text";
        var originalStatus = 200;
        var sharedServer = ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(originalStatus)
                .andTextBody(originalBody)
                .startShared();

        try {
            var borrowedTextBody = "some borrowed text";
            var borrowedStatus = 400;
            var impBorrowed = sharedServer
                    .borrow()
                    .alwaysRespondWithStatus(borrowedStatus)
                    .andTextBody(borrowedTextBody)
                    .andNoAdditionalHeaders();
            impBorrowed.useServer(server -> sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString()));

            var response = sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.body()).isEqualTo(originalBody);
            assertThat(response.statusCode()).isEqualTo(originalStatus);
        } finally {
            sharedServer.dispose();
        }
    }

    @Test
    @DisplayName("borrowed server should reset hits and misses inside closure")
    void borrowed_server_should_reset_hits_and_misses_inside_closure() {
        var originalBody = "some text";
        var originalStatus = 200;
        var expectedHeader = "some-header";
        var headers = Map.of(expectedHeader, List.of("some-value"));
        var sharedServer = ImpServer.template()
                .randomPort()
                .onRequestMatching("anyId", spec -> spec.headersPredicate(h -> h.containsKey(expectedHeader)))
                .respondWithStatus(originalStatus)
                .andTextBody(originalBody)
                .andNoAdditionalHeaders()
                .rejectNonMatching()
                .startShared();

        sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString());
        sendHttpRequestWithHeaders(sharedServer.port(), headers, HttpResponse.BodyHandlers.ofString());
        try {
            var borrowedTextBody = "some borrowed text";
            var borrowedStatus = 400;
            var impBorrowed = sharedServer
                    .borrow()
                    .alwaysRespondWithStatus(borrowedStatus)
                    .andTextBody(borrowedTextBody)
                    .andNoAdditionalHeaders();
            impBorrowed.useServer(server -> {
                var statistics = server.statistics();
                assertThat(statistics.hitCount()).isZero();
                assertThat(statistics.missCount()).isZero();
            });
        } finally {
            sharedServer.dispose();
        }
    }

    @Test
    @DisplayName("borrowed server should restore hits and misses for original server after closure")
    void borrowed_server_should_restore_hits_and_misses_for_original_server_after_closure() {
        var originalBody = "some text";
        var originalStatus = 200;
        var expectedHeader = "some-header";
        var headers = Map.of(expectedHeader, List.of("some-value"));
        var sharedServer = ImpServer.template()
                .randomPort()
                .onRequestMatching("anyId", spec -> spec.headersPredicate(h -> h.containsKey(expectedHeader)))
                .respondWithStatus(originalStatus)
                .andTextBody(originalBody)
                .andNoAdditionalHeaders()
                .rejectNonMatching()
                .startShared();

        sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString());
        sendHttpRequestWithHeaders(sharedServer.port(), headers, HttpResponse.BodyHandlers.ofString());
        try {
            sharedServer
                    .borrow()
                    .alwaysRespondWithStatus(400)
                    .andTextBody("some borrowed text")
                    .andNoAdditionalHeaders()
                    .useServer(server -> {
                        sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString());
                        sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString());
                    });
            var statistics = sharedServer.statistics();

            assertThat(statistics.hitCount()).isEqualTo(1);
            assertThat(statistics.missCount()).isEqualTo(1);
        } finally {
            sharedServer.dispose();
        }
    }

    @Test
    @DisplayName("borrowed server should store statistics inside and after closure")
    void borrowed_server_should_store_statistics_inside_and_after_closure() {
        var originalBody = "some text";
        var sharedServer = ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody(originalBody)
                .startShared();
        try {
            var statistics = sharedServer
                    .borrow()
                    .alwaysRespondWithStatus(400)
                    .andTextBody("some borrowed text")
                    .andNoAdditionalHeaders()
                    .useServer(server -> {
                        assertThat(server.statistics().hitCount()).isZero();
                        sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString());
                        assertThat(server.statistics().hitCount()).isOne();
                        sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString());
                        assertThat(server.statistics().hitCount()).isEqualTo(2);
                        assertThat(server.statistics().missCount()).isZero();
                    });
            assertThat(statistics.hitCount()).isEqualTo(2);
            assertThat(statistics.missCount()).isZero();
        } finally {
            sharedServer.dispose();
        }
    }

    @Test
    @DisplayName("when shared server is stopped, then dont accept further requests")
    void when_shared_server_is_stopped_then_dont_accept_further_requests() throws Exception {
        var sharedServer = ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody("some text")
                .startShared();
        sharedServer.dispose();

        var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", sharedServer.port())))
                .build();
        assertThatThrownBy(() -> sendHttpRequest(request, HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(ConnectException.class);
    }

    @Test
    @DisplayName("when shared server is stopped many times, then no exception thrown and server is still stopped")
    void when_shared_server_is_stopped_many_times_then_no_exception_thrown_and_server_is_still_stopped()
            throws Exception {
        var sharedServer = ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody("some text")
                .startShared();
        sharedServer.dispose();
        sharedServer.dispose();
        sharedServer.dispose();
        sharedServer.dispose();

        var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", sharedServer.port())))
                .build();
        assertThatThrownBy(() -> sendHttpRequest(request, HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(ConnectException.class);
    }

    @Test
    @DisplayName("'alwaysRespondWithStatus' should fail immediately if provided invalid http status")
    void alwaysrespondwithstatus_should_fail_immediately_if_provided_invalid_http_status() {
        for (var invalidHttpStatusCode : List.of(-1, 1, 99, 104, 512, Integer.MAX_VALUE)) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as("should reject http status code [%d]", invalidHttpStatusCode)
                    .isThrownBy(() -> ImpServer.template().randomPort().alwaysRespondWithStatus(invalidHttpStatusCode))
                    .withMessage("Invalid http status code [%d]", invalidHttpStatusCode);
        }
    }

    @Test
    @DisplayName("'alwaysRespondWithStatus' should work all known status codes")
    void alwaysrespondwithstatus_should_work_all_known_status_codes() {
        for (var httpStatus : ImpHttpStatus.values()) {
            assertThatNoException()
                    .as("Should work for http status code [%d]", httpStatus.value())
                    .isThrownBy(() -> ImpServer.template().randomPort().alwaysRespondWithStatus(httpStatus.value()));
        }
    }

    @Test
    @DisplayName("'andTextBody' should fail immediately if provided null")
    void andtextbody_should_fail_immediately_if_provided_null() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .alwaysRespondWithStatus(200)
                        .andTextBody(null))
                .withMessage("nulls are not supported - textBody");
    }

    @Test
    @DisplayName("'andXmlBody' should fail immediately if provided null")
    void andxmlbody_should_fail_immediately_if_provided_null() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .alwaysRespondWithStatus(200)
                        .andXmlBody(null))
                .withMessage("nulls are not supported - xmlBody");
    }

    @Test
    @DisplayName("'andJsonBody' should fail immediately if provided null")
    void andjsonbody_should_fail_immediately_if_provided_null() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .alwaysRespondWithStatus(200)
                        .andJsonBody(null))
                .withMessage("nulls are not supported - jsonBody");
    }

    @Test
    @DisplayName("'andCustomContentTypeStream' should fail immediately if provided null contentType")
    void andcustomcontenttypestream_should_fail_immediately_if_provided_null_contenttype() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .alwaysRespondWithStatus(200)
                        .andCustomContentTypeStream(null, () -> new ByteArrayInputStream(new byte[0])))
                .withMessage("null or blank strings are not supported - contentType");
    }

    @Test
    @DisplayName("'andCustomContentTypeStream' should fail immediately if provided null streamSupplier")
    void andcustomcontenttypestream_should_fail_immediately_if_provided_null_streamsupplier() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .alwaysRespondWithStatus(200)
                        .andCustomContentTypeStream("someType", null))
                .withMessage("nulls are not supported - dataStreamSupplier");
    }

    @Test
    @DisplayName(
            "'andCustomContentTypeStream' should fail immediately if provided null streamSupplier and contentTypeqq")
    void andcustomcontenttypestream_should_fail_immediately_if_provided_null_streamsupplier_and_contenttypeqq() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .alwaysRespondWithStatus(200)
                        .andCustomContentTypeStream(null, null))
                .withMessage("null or blank strings are not supported - contentType");
    }

    @Test
    @DisplayName("'andCustomContentTypeStream' should fail immediately if provided empty contentType")
    void andcustomcontenttypestream_should_fail_immediately_if_provided_empty_contenttype() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .alwaysRespondWithStatus(200)
                        .andCustomContentTypeStream("", () -> new ByteArrayInputStream(new byte[0])))
                .withMessage("null or blank strings are not supported - contentType");
    }

    @Test
    @DisplayName("'andCustomContentTypeStream' should fail immediately if provided blank contentType")
    void andcustomcontenttypestream_should_fail_immediately_if_provided_blank_contenttype() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .alwaysRespondWithStatus(200)
                        .andCustomContentTypeStream("  ", () -> new ByteArrayInputStream(new byte[0])))
                .withMessage("null or blank strings are not supported - contentType");
    }

    @Test
    @DisplayName("'onRequestMatching' when null then throw exception")
    void onrequestmatching_when_null_then_throw_exception() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template().randomPort().onRequestMatching("id", null))
                .withMessage("nulls are not supported - consumer");
    }

    @Test
    @DisplayName("'onRequestMatching' when null id then throw exception")
    void onrequestmatching_when_null_id_then_throw_exception() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template().randomPort().onRequestMatching(null, b -> {}))
                .withMessage("null or blank strings are not supported - id");
    }

    @Test
    @DisplayName("'onRequestMatching' when empty id then throw exception")
    void onrequestmatching_when_empty_id_then_throw_exception() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template().randomPort().onRequestMatching("", b -> {}))
                .withMessage("null or blank strings are not supported - id");
    }

    @Test
    @DisplayName("'onRequestMatching' when blank id then throw exception")
    void onrequestmatching_when_blank_id_then_throw_exception() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template().randomPort().onRequestMatching("  ", b -> {}))
                .withMessage("null or blank strings are not supported - id");
    }

    @Test
    @DisplayName("'onRequestMatching' when noop consumer then ok")
    void onrequestmatching_when_noop_consumer_then_ok() {
        assertThatNoException()
                .isThrownBy(() -> ImpServer.template().randomPort().onRequestMatching("id", builder -> {}));
    }

    @Test
    @DisplayName("'onRequestMatching' when consumer sets null headers predicate, then fail immediately")
    void onrequestmatching_when_consumer_sets_null_headers_predicate_then_fail_immediately() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", builder -> builder.headersPredicate(null)))
                .withMessage("nulls are not supported - headersPredicate");
    }

    @Test
    @DisplayName("'onRequestMatching' when consumer sets normal headers predicate then ok")
    void onrequestmatching_when_consumer_sets_normal_headers_predicate_then_ok() {
        assertThatNoException().isThrownBy(() -> ImpServer.template()
                .randomPort()
                .onRequestMatching("id", builder -> builder.headersPredicate(p -> true)));
    }

    @Test
    @DisplayName("'onRequestMatching' should fail immediately if provided invalid http status")
    void onrequestmatching_should_fail_immediately_if_provided_invalid_http_status() {
        for (var invalidHttpStatusCode : List.of(-1, 1, 99, 104, 512, Integer.MAX_VALUE)) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as("should reject http status code [%d]", invalidHttpStatusCode)
                    .isThrownBy(() -> ImpServer.template()
                            .randomPort()
                            .onRequestMatching("id", r -> {})
                            .respondWithStatus(invalidHttpStatusCode))
                    .withMessage("Invalid http status code [%d]", invalidHttpStatusCode);
        }
    }

    @Test
    @DisplayName("'onRequestMatching' should work all known status codes")
    void onrequestmatching_should_work_all_known_status_codes() {
        for (var httpStatus : ImpHttpStatus.values()) {
            assertThatNoException()
                    .as("Should work for http status code [%d]", httpStatus.value())
                    .isThrownBy(() -> ImpServer.template()
                            .randomPort()
                            .onRequestMatching("id", r -> {})
                            .respondWithStatus(httpStatus.value()));
        }
    }

    @Test
    @DisplayName("'onRequestMatching andTextBody' should reject null")
    void onrequestmatching_andtextbody_should_reject_null() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andTextBody(null))
                .withMessage("nulls are not supported - textBody");
    }

    @Test
    @DisplayName("'onRequestMatching andJsonBody' should reject null")
    void onrequestmatching_andjsonbody_should_reject_null() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andJsonBody(null))
                .withMessage("nulls are not supported - jsonBody");
    }

    @Test
    @DisplayName("'onRequestMatching andXmlBody' should reject null")
    void onrequestmatching_andxmlbody_should_reject_null() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andXmlBody(null))
                .withMessage("nulls are not supported - xmlBody");
    }

    @Test
    @DisplayName("'onRequestMatching customContentTypeStream' should reject null content type")
    void onrequestmatching_customcontenttypestream_should_reject_null_content_type() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andCustomContentTypeStream(null, () -> new ByteArrayInputStream(new byte[0])))
                .withMessage("null or blank strings are not supported - contentType");
    }

    @Test
    @DisplayName("'onRequestMatching customContentTypeStream' should reject empty content type")
    void onrequestmatching_customcontenttypestream_should_reject_empty_content_type() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andCustomContentTypeStream("", () -> new ByteArrayInputStream(new byte[0])))
                .withMessage("null or blank strings are not supported - contentType");
    }

    @Test
    @DisplayName("'onRequestMatching customContentTypeStream' should reject blank content type")
    void onrequestmatching_customcontenttypestream_should_reject_blank_content_type() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andCustomContentTypeStream("  ", () -> new ByteArrayInputStream(new byte[0])))
                .withMessage("null or blank strings are not supported - contentType");
    }

    @Test
    @DisplayName("'onRequestMatching customContentTypeStream' should reject null dataStreamSupplier")
    void onrequestmatching_customcontenttypestream_should_reject_null_datastreamsupplier() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andCustomContentTypeStream("contentType", null))
                .withMessage("nulls are not supported - dataStreamSupplier");
    }

    @Test
    @DisplayName("'onRequestMatching andDataStreamBody' should reject null dataStreamBody")
    void onrequestmatching_anddatastreambody_should_reject_null_datastreambody() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andDataStreamBody(null))
                .withMessage("nulls are not supported - dataStreamSupplier");
    }

    @Test
    @DisplayName("'onRequestMatching customContentTypeStream' should reject null contentType with dataStreamSupplier")
    void onrequestmatching_customcontenttypestream_should_reject_null_contenttype_with_datastreamsupplier() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andCustomContentTypeStream(null, null))
                .withMessage("null or blank strings are not supported - contentType");
    }

    @Test
    @DisplayName("'onRequestMatching andAdditionalHeaders' should reject null headers")
    void onrequestmatching_andadditionalheaders_should_reject_null_headers() {
        //noinspection DataFlowIssue
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andTextBody("")
                        .andAdditionalHeaders(null))
                .withMessage("nulls are not supported - headers");
    }

    @Test
    @DisplayName("'onRequestMatching andAdditionalHeaders' should reject nulls entry in map immediately")
    void onrequestmatching_andadditionalheaders_should_reject_nulls_entry_in_map_immediately() {
        var headers = new HashMap<String, List<String>>();
        headers.put(null, null);
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andTextBody("")
                        .andAdditionalHeaders(headers))
                .withMessage("null key are not supported in headers, but found null key in entry [ null=null ]");
    }

    @Test
    @DisplayName("'onRequestMatching andAdditionalHeaders' should reject nulls keys in map immediately")
    void onrequestmatching_andadditionalheaders_should_reject_nulls_keys_in_map_immediately() {
        var headers = new HashMap<String, List<String>>();
        headers.put(null, List.of("something"));
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andTextBody("")
                        .andAdditionalHeaders(headers))
                .withMessage("null key are not supported in headers, but found null key in entry [ null=[something] ]");
    }

    @Test
    @DisplayName("'onRequestMatching andAdditionalHeaders' should reject nulls values in map immediately")
    void onrequestmatching_andadditionalheaders_should_reject_nulls_values_in_map_immediately() {
        var headers = new HashMap<String, List<String>>();
        headers.put("something", null);
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> ImpServer.template()
                        .randomPort()
                        .onRequestMatching("id", r -> {})
                        .respondWithStatus(200)
                        .andTextBody("")
                        .andAdditionalHeaders(headers))
                .withMessage(
                        "null values are not supported in headers, but found null values in entry [ something=null ]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user-agent", "User-Agent", "uSeR-AgeNT"})
    @DisplayName("should return expected response when matched user-agent header key by 'containsKey'")
    void should_return_expected_response_when_matched_user_agent_header_key_by_containskey(
            String contentTypeHeaderKey) {
        var expected = "some text";
        ImpServer.template()
                .randomPort()
                .onRequestMatching("id", request -> request.headersPredicate(h -> h.containsKey(contentTypeHeaderKey)))
                .respondWithStatus(200)
                .andTextBody(expected)
                .andNoAdditionalHeaders()
                .rejectNonMatching()
                .useServer(impServer -> {
                    var request = HttpRequest.newBuilder(
                                    new URI(String.format("http://localhost:%d/", impServer.port())))
                            .build();
                    var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(expected);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers).containsEntry("Content-Type", List.of("text/plain"));
                        assertThat(headers).containsEntry("Content-Length", List.of("9"));
                        assertThat(headers).containsKey("date");
                    });
                });
    }

    @Test
    @DisplayName("should return fallback response when none of matchers matched request and expected text body")
    void should_return_fallback_response_when_none_of_matchers_matched_request_and_expected_text_body() {
        var matcherId = "some matcher id";
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching(
                        matcherId,
                        request -> request.headersPredicate(h -> h.containsKey("unknown-not-matched-header")))
                .respondWithStatus(200)
                .andTextBody("should never return")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                    .build();
            var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.body())
                    .isEqualTo(
                            "No matching handler for request. Returning 418 [I'm a teapot]. Available matcher IDs: [%s]",
                            matcherId);
            assertThat(response.statusCode()).isEqualTo(418);
            assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                assertThat(headers).containsEntry("Content-Type", List.of("text/plain"));
                assertThat(headers).containsKey("Content-Length");
                assertThat(headers).containsKey("date");
            });
        });
    }

    @Test
    @DisplayName("should return fallback response when none of matchers matched request and expected json body")
    void should_return_fallback_response_when_none_of_matchers_matched_request_and_expected_json_body() {
        var matcherId = "some matcher id";
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching(
                        matcherId,
                        request -> request.headersPredicate(h -> h.containsKey("unknown-not-matched-header")))
                .respondWithStatus(200)
                .andJsonBody("{}")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                    .build();
            var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.body())
                    .isEqualTo(
                            "No matching handler for request. Returning 418 [I'm a teapot]. Available matcher IDs: [%s]",
                            matcherId);
            assertThat(response.statusCode()).isEqualTo(418);
            assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                assertThat(headers).containsEntry("Content-Type", List.of("text/plain"));
                assertThat(headers).containsKey("Content-Length");
                assertThat(headers).containsKey("date");
            });
        });
    }

    @Test
    @DisplayName(
            "should return expected response when matched user-agent header key by 'containsKey' and expect json body")
    void should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_json_body() {
        @Language("json")
        var expected = "{ \"some\": \"json\" }";
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching("id", request -> request.headersPredicate(h -> h.containsKey("user-agent")))
                .respondWithStatus(200)
                .andJsonBody(expected)
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                    .build();
            var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.body()).isEqualTo(expected);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                assertThat(headers).containsEntry("Content-Type", List.of("application/json"));
                assertThat(headers).containsEntry("Content-Length", List.of(expected.length() + ""));
                assertThat(headers).containsKey("date");
            });
        });
    }

    @Test
    @DisplayName(
            "should return expected response when matched user-agent header key by 'containsKey' and expect xml body")
    void should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_xml_body() {
        @Language("xml")
        var expected = "<root><entry>value</entry></root>";
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching("id", request -> request.headersPredicate(h -> h.containsKey("user-agent")))
                .respondWithStatus(200)
                .andXmlBody(expected)
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                    .build();
            var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.body()).isEqualTo(expected);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                assertThat(headers).containsEntry("Content-Type", List.of("application/xml"));
                assertThat(headers).containsEntry("Content-Length", List.of(expected.length() + ""));
                assertThat(headers).containsKey("date");
            });
        });
    }

    @Test
    @DisplayName(
            "should return expected response when matched user-agent header key by 'containsKey' and expect stream body")
    void should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_stream_body() {
        var expected = "some-data".getBytes(StandardCharsets.UTF_8);
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching("id", request -> request.headersPredicate(h -> h.containsKey("user-agent")))
                .respondWithStatus(200)
                .andDataStreamBody(() -> new ByteArrayInputStream(expected))
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                    .build();
            var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.body()).isEqualTo(new String(expected, StandardCharsets.UTF_8));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                assertThat(headers).containsEntry("Content-Type", List.of("application/octet-stream"));
                assertThat(headers).containsEntry("Content-Length", List.of(expected.length + ""));
                assertThat(headers).containsKey("date");
            });
        });
    }

    @Test
    @DisplayName(
            "should return expected response when matched user-agent header key by 'containsKey' and expect custom content type")
    void
            should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_custom_content_type() {
        var expected = "some-data".getBytes(StandardCharsets.UTF_8);
        var contentType = "customContentType";
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching("id", request -> request.headersPredicate(h -> h.containsKey("user-agent")))
                .respondWithStatus(200)
                .andCustomContentTypeStream(contentType, () -> new ByteArrayInputStream(expected))
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                    .build();
            var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.body()).isEqualTo(new String(expected, StandardCharsets.UTF_8));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                assertThat(headers).containsEntry("Content-Type", List.of(contentType));
                assertThat(headers).containsEntry("Content-Length", List.of(expected.length + ""));
                assertThat(headers).containsKey("date");
            });
        });
    }

    @Test
    @DisplayName(
            "should return expected status when matched user-agent header key by 'containsKey' and expect specific status")
    void should_return_expected_status_when_matched_user_agent_header_key_by_containskey_and_expect_specific_status() {
        var expectedStatus = 404;
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching("any", request -> request.headersPredicate(h -> h.containsKey("user-agent")))
                .respondWithStatus(expectedStatus)
                .andTextBody("any")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.body()).isEqualTo("any");
            assertThat(response.statusCode()).isEqualTo(expectedStatus);
        });
    }

    @Test
    @DisplayName(
            "should return additional headers when matched user-agent header key by 'containsKey' and expect additional headers")
    void
            should_return_additional_headers_when_matched_user_agent_header_key_by_containskey_and_expect_additional_headers() {
        var additionalHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2", "value3"));
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching("any", request -> request.headersPredicate(h -> h.containsKey("user-agent")))
                .respondWithStatus(200)
                .andTextBody("any")
                .andAdditionalHeaders(additionalHeaders)
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString());
            var responseHeaders = response.headers().map();
            assertThat(response.body()).isEqualTo("any");
            assertThat(responseHeaders).hasSizeGreaterThan(additionalHeaders.size());
            assertThat(responseHeaders).containsAllEntriesOf(additionalHeaders);
            assertThat(responseHeaders).containsEntry("Content-Type", List.of("text/plain"));
        });
    }

    @Test
    @DisplayName(
            "should return only exact headers when matched user-agent header key by 'containsKey' and expect exact headers")
    void should_return_only_exact_headers_when_matched_user_agent_header_key_by_containskey_and_expect_exact_headers() {
        var exactHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2", "value3"));
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching("any", request -> request.headersPredicate(h -> h.containsKey("user-agent")))
                .respondWithStatus(200)
                .andTextBody("any")
                .andExactHeaders(exactHeaders)
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString());
            var responseHeaders = response.headers().map();
            assertThat(response.body()).isEqualTo("any");
            assertThat(responseHeaders).hasSize(4);
            assertThat(responseHeaders).containsAllEntriesOf(exactHeaders);
            assertThat(responseHeaders).containsKey("content-length");
            assertThat(responseHeaders).containsKey("date");
        });
    }

    @Test
    @DisplayName("should successfuly match by headers predicate 'containsValue'")
    void should_successfuly_match_by_headers_predicate_containsvalue() {
        var expectedMatchValue = "value2";
        var sentHeaders = Map.of("header1", List.of("value1", expectedMatchValue));
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching("any", request -> request.headersPredicate(h -> h.containsValue(expectedMatchValue)))
                .respondWithStatus(200)
                .andTextBody("any")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response =
                    sendHttpRequestWithHeaders(impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString());
            var responseHeaders = response.headers().map();
            assertThat(response.body()).isEqualTo("any");
            assertThat(responseHeaders).hasSize(3);
            assertThat(responseHeaders).containsEntry("Content-Type", List.of("text/plain"));
        });
    }

    @Test
    @DisplayName("should return error when can't match by headers predicate 'containsValue'")
    void should_return_error_when_can_t_match_by_headers_predicate_containsvalue() {
        var sentHeaders = Map.of("header1", List.of("value1", "value2"), "header2", List.of("value3"));
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching(
                        "anyId", request -> request.headersPredicate(h -> h.containsValue("some not existing value")))
                .respondWithStatus(200)
                .andTextBody("anyBody")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response =
                    sendHttpRequestWithHeaders(impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(ImpHttpStatus.I_AM_A_TEAPOT.value());
            assertThat(response.body())
                    .isEqualTo(
                            "No matching handler for request. Returning 418 [I'm a teapot]. Available matcher IDs: [anyId]");
        });
    }

    @Test
    @DisplayName("should return error when can't match by headers predicate 'containsPair'")
    void should_return_error_when_can_t_match_by_headers_predicate_containspair() {
        var sentHeaders = Map.of("header1", List.of("value1", "value2"), "header2", List.of("value2", "value3"));
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching(
                        "anyId",
                        request -> request.headersPredicate(h -> h.containsPair("header1", "some not existing value")))
                .respondWithStatus(200)
                .andTextBody("anyBody")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response =
                    sendHttpRequestWithHeaders(impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(ImpHttpStatus.I_AM_A_TEAPOT.value());
            assertThat(response.body())
                    .isEqualTo(
                            "No matching handler for request. Returning 418 [I'm a teapot]. Available matcher IDs: [anyId]");
        });
    }

    @Test
    @DisplayName("should successfuly match by headers predicate 'containsPair'")
    void should_successfuly_match_by_headers_predicate_containspair() {
        var expectedMatchPair = Map.entry("header2", "value3");
        var sentHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2", "value3"));
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching(
                        "any",
                        request -> request.headersPredicate(
                                h -> h.containsPair(expectedMatchPair.getKey(), expectedMatchPair.getValue())))
                .respondWithStatus(200)
                .andTextBody("any")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response =
                    sendHttpRequestWithHeaders(impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString());
            var responseHeaders = response.headers().map();
            assertThat(response.body()).isEqualTo("any");
            assertThat(responseHeaders).hasSize(3);
            assertThat(responseHeaders).containsEntry("Content-Type", List.of("text/plain"));
        });
    }

    @Test
    @DisplayName("should return error when can't match by headers predicate 'containsPairList'")
    void should_return_error_when_can_t_match_by_headers_predicate_containspairlist() {
        var sentHeaders = Map.of("header1", List.of("value1", "value2"), "header2", List.of("value2", "value3"));
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching(
                        "anyId",
                        request -> request.headersPredicate(
                                h -> h.containsPairList("header1", List.of("some not existing value"))))
                .respondWithStatus(200)
                .andTextBody("anyBody")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response =
                    sendHttpRequestWithHeaders(impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(ImpHttpStatus.I_AM_A_TEAPOT.value());
            assertThat(response.body())
                    .isEqualTo(
                            "No matching handler for request. Returning 418 [I'm a teapot]. Available matcher IDs: [anyId]");
        });
    }

    @Test
    @DisplayName("should successfuly match by headers predicate 'containsPairList'")
    void should_successfuly_match_by_headers_predicate_containspairlist() {
        var expectedMatchPair = Map.entry("header2", List.of("value2", "value3"));
        var sentHeaders =
                Map.of("header1", List.of("value1"), expectedMatchPair.getKey(), expectedMatchPair.getValue());
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching(
                        "any",
                        request -> request.headersPredicate(
                                h -> h.containsPairList(expectedMatchPair.getKey(), expectedMatchPair.getValue())))
                .respondWithStatus(200)
                .andTextBody("any")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response =
                    sendHttpRequestWithHeaders(impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString());
            var responseHeaders = response.headers().map();
            assertThat(response.body()).isEqualTo("any");
            assertThat(responseHeaders).hasSize(3);
            assertThat(responseHeaders).containsEntry("Content-Type", List.of("text/plain"));
        });
    }

    @Test
    @DisplayName("should successfully match by headers predicate 'hasContentType'")
    void should_successfully_match_by_headers_predicate_hascontenttype() {
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching("any", request -> request.headersPredicate(h -> h.hasContentType("text/plain")))
                .respondWithStatus(200)
                .andTextBody("any")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response = sendHttpRequestWithHeaders(
                    impServer.port(),
                    Map.of("content-type", List.of("text/plain")),
                    HttpResponse.BodyHandlers.ofString());
            var responseHeaders = response.headers().map();
            assertThat(response.body()).isEqualTo("any");
            assertThat(responseHeaders).hasSize(3);
            assertThat(responseHeaders).containsEntry("Content-Type", List.of("text/plain"));
        });
    }

    @Test
    @DisplayName("should return error when 'hasContentType' doesn't match")
    void should_return_error_when_hascontenttype_doesn_t_match() {
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching(
                        "anyId", request -> request.headersPredicate(h -> h.hasContentType("application/json")))
                .respondWithStatus(200)
                .andTextBody("any")
                .andNoAdditionalHeaders()
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(ImpHttpStatus.I_AM_A_TEAPOT.value());
            assertThat(response.body())
                    .isEqualTo(
                            "No matching handler for request. Returning 418 [I'm a teapot]. Available matcher IDs: [anyId]");
        });
    }

    @Test
    @DisplayName("should return error when 'hasContentType' specified, but contentType is null in request")
    void should_return_error_when_hascontenttype_specified_but_contenttype_is_null_in_request() {
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching(
                        "anyId", request -> request.headersPredicate(h -> h.hasContentType("application/json")))
                .respondWithStatus(200)
                .andTextBody("any")
                .andExactHeaders(Map.of())
                .rejectNonMatching();
        subject.useServer(impServer -> {
            var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(ImpHttpStatus.I_AM_A_TEAPOT.value());
            assertThat(response.body())
                    .isEqualTo(
                            "No matching handler for request. Returning 418 [I'm a teapot]. Available matcher IDs: [anyId]");
        });
    }

    @Test
    @DisplayName(
            "should return fallback when 'hasContentType' specified, but contentType is null in request and fallback specified")
    void
            should_return_fallback_when_hascontenttype_specified_but_contenttype_is_null_in_request_and_fallback_specified() {
        var fallbackStatus = ImpHttpStatus.BAD_REQUEST;
        var fallbackBody = "fallback";
        var subject = ImpServer.template()
                .randomPort()
                .onRequestMatching(
                        "anyId", request -> request.headersPredicate(h -> h.hasContentType("application/json")))
                .respondWithStatus(200)
                .andTextBody("any")
                .andExactHeaders(Map.of())
                .fallbackForNonMatching(builder -> builder.status(fallbackStatus.value())
                        .body(() -> fallbackBody.getBytes(StandardCharsets.UTF_8)));
        subject.useServer(impServer -> {
            var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(fallbackStatus.value());
            assertThat(response.body()).isEqualTo(fallbackBody);
        });
    }
}
