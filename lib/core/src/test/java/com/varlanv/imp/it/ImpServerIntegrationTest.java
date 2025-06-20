package com.varlanv.imp.it;

import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.*;

import com.jayway.jsonpath.InvalidPathException;
import com.varlanv.imp.*;
import com.varlanv.imp.commontest.BaseTest;
import com.varlanv.imp.commontest.FastTest;
import com.varlanv.imp.commontest.LazyCloseAwareStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImpServerIntegrationTest implements FastTest {

    @Nested
    class TemplateSuite implements FastTest {

        @Test
        @DisplayName("Should be able to start server with random port")
        void should_be_able_to_start_server_with_random_port() {
            ImpServer.httpTemplate()
                    .alwaysRespond(spec -> spec.withStatus(200).andTextBody("").andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {});
        }

        @Test
        @DisplayName("Fresh server should have zero hits and misses")
        void fresh_server_should_have_zero_hits_and_misses() {
            ImpServer.httpTemplate()
                    .alwaysRespond(spec -> spec.withStatus(200).andTextBody("").andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {
                        assertThat(impServer.statistics().hitCount()).isZero();
                        assertThat(impServer.statistics().missCount()).isZero();
                    });
        }

        @Test
        @DisplayName("should return random ports each time for template with random port")
        void should_return_random_ports_each_time_for_template_with_random_port() {
            var ports = new ConcurrentLinkedQueue<Integer>();
            var impTemplate = ImpServer.httpTemplate()
                    .alwaysRespond(spec -> spec.withStatus(200).andTextBody("").andNoAdditionalHeaders())
                    .onRandomPort();

            var serversCount = 10;
            IntStream.range(0, serversCount)
                    .forEach(i -> impTemplate.useServer(impServer -> ports.add(impServer.port())));

            assertThat(ports).hasSize(serversCount);

            assertThat(Set.copyOf(ports))
                    .as(
                            """
        This test case make conservative assumption that
        out of %d started servers with random ports,
        at least 3 should have unique port""",
                            serversCount)
                    .hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("When server has one hit, should add it to statistics")
        void when_server_has_one_hit_should_add_it_to_statistics() {
            ImpServer.httpTemplate()
                    .alwaysRespond(spec ->
                            spec.withStatus(200).andTextBody("some body").andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {
                        var request = HttpRequest.newBuilder(
                                        new URI(String.format("http://localhost:%d/", impServer.port())))
                                .build();
                        sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();

                        assertThat(impServer.statistics().hitCount()).isOne();
                        assertThat(impServer.statistics().missCount()).isZero();
                    });
        }

        @Test
        @DisplayName(
                "When server has zero hits, then read statistics, then make hit - then should not modify original statistic")
        void when_server_has_zero_hits_then_read_statistics_then_make_hit_then_should_not_modify_original_statistic() {
            ImpServer.httpTemplate()
                    .alwaysRespond(spec ->
                            spec.withStatus(200).andTextBody("some body").andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {
                        var statistics = impServer.statistics();
                        var request = HttpRequest.newBuilder(
                                        new URI(String.format("http://localhost:%d/", impServer.port())))
                                .build();
                        sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();

                        assertThat(statistics.hitCount()).isZero();
                        assertThat(statistics.missCount()).isZero();
                    });
        }

        @Test
        @DisplayName(
                "When server has one hit, then read statistics, then make another hit - then should not modify original statistic")
        void
                when_server_has_one_hit_then_read_statistics_then_make_another_hit_then_should_not_modify_original_statistic() {
            ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody("somePort").andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {
                        var request = HttpRequest.newBuilder(
                                        new URI(String.format("http://localhost:%d/", impServer.port())))
                                .build();
                        sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();

                        var statistics = impServer.statistics();
                        assertThat(statistics.hitCount()).isOne();
                        assertThat(statistics.missCount()).isZero();

                        sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();
                        assertThat(statistics.hitCount()).isOne();
                        assertThat(statistics.missCount()).isZero();
                    });
        }

        @Test
        @DisplayName("When server has many hits, should add all to statistics")
        void when_server_has_many_hits_should_add_all_to_statistics() {
            ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody("somePort").andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {
                        var count = 50;
                        var futures = new CompletableFuture<?>[count];
                        for (var i = 0; i < count; i++) {
                            var request = HttpRequest.newBuilder(
                                            new URI(String.format("http://localhost:%d/", impServer.port())))
                                    .build();
                            futures[i] = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString());
                        }
                        CompletableFuture.allOf(futures).join();

                        assertThat(impServer.statistics().hitCount()).isEqualTo(count);
                        assertThat(impServer.statistics().missCount()).isZero();
                    });
        }

        @Nested
        @Isolated
        class ConcurrencySuite {

            @Test
            @DisplayName("When sanding many requests in parallel, should count all statistic")
            void when_sanding_many_requests_in_parallel_should_count_all_statistic() {
                ImpServer.httpTemplate()
                        .alwaysRespond(spec ->
                                spec.withStatus(200).andTextBody("somePort").andNoAdditionalHeaders())
                        .onRandomPort()
                        .useServer(impServer -> {
                            var count = 50;
                            var latchCounter = new CountDownLatch(count);
                            var allReadyLatch = new CountDownLatch(1);
                            var executorService = Executors.newFixedThreadPool(count);
                            var futures = new CompletableFuture<?>[count];
                            try {
                                for (var i = 0; i < count; i++) {
                                    futures[i] = CompletableFuture.runAsync(
                                            () -> {
                                                try {
                                                    latchCounter.countDown();
                                                    allReadyLatch.await();
                                                    var request = HttpRequest.newBuilder(new URI(String.format(
                                                                    "http://localhost:%d/", impServer.port())))
                                                            .build();
                                                    sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                                            .join();
                                                } catch (Exception e) {
                                                    BaseTest.hide(e);
                                                }
                                            },
                                            executorService);
                                }
                                if (!latchCounter.await(5, TimeUnit.SECONDS)) {
                                    throw new TimeoutException("Failed to start fixture");
                                }
                                allReadyLatch.countDown();
                                CompletableFuture.allOf(futures).join();
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

            ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andJsonBody(expected).andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {
                        var request = httpRequestBuilderSupplier
                                .apply(impServer.port())
                                .build();
                        var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();

                        expectSelfie(responseToString(response)).toMatchDisk();
                    });
        }

        @ParameterizedTest
        @ArgumentsSource(HttpRequestBuilderSource.class)
        @DisplayName("server should response with expected custom content type")
        void server_should_response_with_expected_custom_content_type(
                Function<Integer, HttpRequest.Builder> httpRequestBuilderSupplier) {
            var expected = "some text";
            var contentType = "some/content/type";

            ImpServer.httpTemplate()
                    .alwaysRespond(spec -> spec.withStatus(200)
                            .andCustomContentTypeStream(
                                    contentType,
                                    () -> new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8)))
                            .andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {
                        var request = httpRequestBuilderSupplier
                                .apply(impServer.port())
                                .build();
                        var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();

                        assertThat(response.body()).isEqualTo(expected);
                        assertThat(response.statusCode()).isEqualTo(200);
                        assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                            assertThat(headers).containsEntry("Content-Type", List.of(contentType));
                            assertThat(headers)
                                    .containsEntry("Content-Length", List.of(String.valueOf(expected.length())));
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

            ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody(expected).andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {
                        var request = httpRequestBuilderSupplier
                                .apply(impServer.port())
                                .build();
                        var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();

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

            ImpServer.httpTemplate()
                    .alwaysRespond(spec -> spec.withStatus(200)
                            .andDataStreamBody(
                                    () -> new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8)))
                            .andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {
                        var request = httpRequestBuilderSupplier
                                .apply(impServer.port())
                                .build();
                        var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();

                        assertThat(response.body()).isEqualTo(expected);
                        assertThat(response.statusCode()).isEqualTo(200);
                        assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                            assertThat(headers).containsEntry("Content-Type", List.of("application/octet-stream"));
                            assertThat(headers)
                                    .containsEntry("Content-Length", List.of(String.valueOf(expected.length())));
                            assertThat(headers).containsKey("date");
                        });
                    });
        }

        @Test
        @DisplayName("template should be able to reuse file stream supplier from `andDataStreamBody`")
        void template_should_be_able_to_reuse_file_stream_supplier_from_anddatastreambody() {
            var expectedText = "some text";
            consumeTempFile(tempFile -> {
                Files.writeString(tempFile, expectedText);
                ImpServer.httpTemplate()
                        .alwaysRespond(spec -> spec.withStatus(200)
                                .andDataStreamBody(() -> Files.newInputStream(tempFile))
                                .andNoAdditionalHeaders())
                        .onRandomPort()
                        .useServer(impServer -> {
                            for (var futureResponse :
                                    sendManyHttpRequests(5, impServer.port(), HttpResponse.BodyHandlers.ofString())) {
                                var response = futureResponse.join();

                                assertThat(response.body()).isEqualTo(expectedText);
                                assertThat(response.statusCode()).isEqualTo(200);
                                assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                                    assertThat(headers)
                                            .containsEntry("Content-Type", List.of("application/octet-stream"));
                                    assertThat(headers)
                                            .containsEntry(
                                                    "Content-Length", List.of(String.valueOf(expectedText.length())));
                                    assertThat(headers).containsKey("date");
                                });
                            }
                        });
            });
        }

        @Test
        @DisplayName("should call `close` on stream returned by supplier from `andDataStreamBody`")
        void should_call_close_on_stream_returned_by_supplier_from_anddatastreambody() {
            var expectedText = "some text";
            var dataStream = new LazyCloseAwareStream(
                    () -> new ByteArrayInputStream(expectedText.getBytes(StandardCharsets.UTF_8)));
            consumeTempFile(tempFile -> {
                Files.writeString(tempFile, expectedText);
                ImpServer.httpTemplate()
                        .alwaysRespond(spec -> spec.withStatus(200)
                                .andDataStreamBody(dataStream::get)
                                .andNoAdditionalHeaders())
                        .onRandomPort()
                        .useServer(impServer -> {
                            sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();
                            assertThat(dataStream.isClosed()).isTrue();
                        });
            });
        }

        @Test
        @DisplayName("should call `close` on stream returned by supplier from `andCustomContentTypeStream`")
        void should_call_close_on_stream_returned_by_supplier_from_andcustomcontenttypestream() {
            var expectedText = "some text";
            var dataStream = new LazyCloseAwareStream(
                    () -> new ByteArrayInputStream(expectedText.getBytes(StandardCharsets.UTF_8)));
            consumeTempFile(tempFile -> {
                Files.writeString(tempFile, expectedText);
                ImpServer.httpTemplate()
                        .alwaysRespond(spec -> spec.withStatus(200)
                                .andCustomContentTypeStream("someContent", dataStream::get)
                                .andNoAdditionalHeaders())
                        .onRandomPort()
                        .useServer(impServer -> {
                            sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();
                            assertThat(dataStream.isClosed()).isTrue();
                        });
            });
        }

        @Test
        @DisplayName("`andCustomContentTypeStream` should be able to read from resources stream")
        void andcustomcontenttypestream_should_be_able_to_read_from_resources_stream() throws Exception {
            ImpSupplier<InputStream> resourcesStreamSupplier =
                    () -> Objects.requireNonNull(getClass().getResourceAsStream("/responses/json/response1.json"));
            try (var res = resourcesStreamSupplier.get()) {
                var expectedJson = new String(res.readAllBytes(), StandardCharsets.UTF_8);
                ImpServer.httpTemplate()
                        .alwaysRespond(spec -> spec.withStatus(200)
                                .andCustomContentTypeStream("application/json", resourcesStreamSupplier)
                                .andNoAdditionalHeaders())
                        .onRandomPort()
                        .useServer(impServer -> {
                            var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();
                            assertThat(response.body()).isEqualTo(expectedJson);
                        });
            }
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

            ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andXmlBody(expected).andNoAdditionalHeaders())
                    .onRandomPort()
                    .useServer(impServer -> {
                        var request = httpRequestBuilderSupplier
                                .apply(impServer.port())
                                .build();
                        var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();

                        assertThat(response.body()).isEqualTo(expected);
                        assertThat(response.statusCode()).isEqualTo(200);
                        assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                            assertThat(headers).containsEntry("Content-Type", List.of("application/xml"));
                            assertThat(headers)
                                    .containsEntry("Content-Length", List.of(String.valueOf(expected.length())));
                            assertThat(headers).containsKey("date");
                        });
                    });
        }

        @Test
        @DisplayName("should be able to start server at specific port")
        void should_be_able_to_start_server_at_specific_port() {
            var port = randomPort();
            var someText = "some text";
            ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody(someText).andNoAdditionalHeaders())
                    .onPort(port)
                    .useServer(impServer -> {
                        var request = HttpRequest.newBuilder(
                                        new URI(String.format("http://localhost:%d/", impServer.port())))
                                .build();
                        var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();

                        expectSelfie(responseToString(response)).toMatchDisk();
                    });
        }

        @Test
        @DisplayName("should throw exception when requested port is already in use")
        void should_throw_exception_when_requested_port_is_already_in_use() throws Exception {
            var port = randomPort();
            var sleepDuration = Duration.ofMillis(500);
            var startedLatch = new CountDownLatch(1);
            new Thread(() -> ImpServer.httpTemplate()
                            .alwaysRespond(spec -> spec.withStatus(200)
                                    .andTextBody("some text")
                                    .andNoAdditionalHeaders())
                            .onPort(port)
                            .useServer(impServer -> {
                                startedLatch.countDown();
                                Thread.sleep(sleepDuration);
                            }))
                    .start();

            if (!startedLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Server not started");
            }

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .alwaysRespond(spec -> spec.withStatus(200)
                                    .andTextBody("some text")
                                    .andNoAdditionalHeaders())
                            .onPort(port)
                            .useServer(impServer -> {}))
                    .withMessage("Could not acquire port [%d] after [5] retries - port is in use", port);
        }

        @ParameterizedTest
        @ValueSource(strings = {"user-agent", "User-Agent", "uSeR-AgeNT"})
        @DisplayName("should return expected response when matched user-agent header key by 'containsKey'")
        void should_return_expected_response_when_matched_user_agent_header_key_by_containskey(
                String contentTypeHeaderKey) {
            var expected = "some text";
            ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.headers().containsKey(contentTypeHeaderKey))
                            .respondWithStatus(200)
                            .andTextBody(expected)
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort()
                    .useServer(impServer -> {
                        var request = HttpRequest.newBuilder(
                                        new URI(String.format("http://localhost:%d/", impServer.port())))
                                .build();
                        var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                                .join();

                        expectSelfie(responseToString(response)).toMatchDisk();
                    });
        }

        @Test
        @DisplayName("should return fallback response when none of matchers matched request and expected text body")
        void should_return_fallback_response_when_none_of_matchers_matched_request_and_expected_text_body() {
            var matcherId = "some matcher id";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id(matcherId)
                            .priority(0)
                            .match(match -> match.headers().containsKey("unknown-not-matched-header"))
                            .respondWithStatus(200)
                            .andTextBody("should never return")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();
            subject.useServer(impServer -> {
                var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                        .build();
                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return fallback response when none of matchers matched request and expected json body")
        void should_return_fallback_response_when_none_of_matchers_matched_request_and_expected_json_body() {
            var matcherId = "some matcher id";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id(matcherId)
                            .priority(0)
                            .match(match -> match.headers().containsKey("unknown-not-matched-header"))
                            .respondWithStatus(200)
                            .andJsonBody("{}")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();
            subject.useServer(impServer -> {
                var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                        .build();
                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName(
                "should return expected response when matched user-agent header key by 'containsKey' and expect json body")
        void should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_json_body() {
            @Language("json")
            var expected = "{ \"some\": \"json\" }";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.headers().containsKey("user-agent"))
                            .respondWithStatus(200)
                            .andJsonBody(expected)
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                        .build();
                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName(
                "should return expected response when matched user-agent header key by 'containsKey' and expect xml body")
        void should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_xml_body() {
            @Language("xml")
            var expected = "<root><entry>value</entry></root>";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.headers().containsKey("user-agent"))
                            .respondWithStatus(200)
                            .andXmlBody(expected)
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                        .build();
                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName(
                "should return expected response when matched user-agent header key by 'containsKey' and expect stream body")
        void
                should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_stream_body() {
            var expected = "some-data".getBytes(StandardCharsets.UTF_8);
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.headers().containsKey("user-agent"))
                            .respondWithStatus(200)
                            .andDataStreamBody(() -> new ByteArrayInputStream(expected))
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                        .build();
                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName(
                "should return expected response when matched user-agent header key by 'containsKey' and expect custom content type")
        void
                should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_custom_content_type() {
            var expected = "some-data".getBytes(StandardCharsets.UTF_8);
            var contentType = "customContentType";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.headers().containsKey("user-agent"))
                            .respondWithStatus(200)
                            .andCustomContentTypeStream(contentType, () -> new ByteArrayInputStream(expected))
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                        .build();
                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName(
                "should return expected status when matched user-agent header key by 'containsKey' and expect specific status")
        void
                should_return_expected_status_when_matched_user_agent_header_key_by_containskey_and_expect_specific_status() {
            var expectedStatus = 404;
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.headers().containsKey("user-agent"))
                            .respondWithStatus(expectedStatus)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();
                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName(
                "should return additional headers when matched user-agent header key by 'containsKey' and expect additional headers")
        void
                should_return_additional_headers_when_matched_user_agent_header_key_by_containskey_and_expect_additional_headers() {
            var additionalHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2", "value3"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.headers().containsKey("user-agent"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andAdditionalHeaders(additionalHeaders))
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName(
                "should return only exact headers when matched user-agent header key by 'containsKey' and expect exact headers")
        void
                should_return_only_exact_headers_when_matched_user_agent_header_key_by_containskey_and_expect_exact_headers() {
            var exactHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2", "value3"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("any")
                            .priority(0)
                            .match(match -> match.headers().containsKey("user-agent"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andExactHeaders(exactHeaders))
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfuly match by headers predicate 'containsValue'")
        void should_successfuly_match_by_headers_predicate_containsvalue() {
            var expectedMatchValue = "value2";
            var sentHeaders = Map.of("header1", List.of("value1", expectedMatchValue));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.headers().containsValue(expectedMatchValue))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should be able to start server with matchers on specific port")
        void should_be_able_to_start_server_with_matchers_on_specific_port() {
            var expectedMatchValue = "value2";
            var sentHeaders = Map.of("header1", List.of("value1", expectedMatchValue));
            var port = randomPort();
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.headers().containsValue(expectedMatchValue))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onPort(port);

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when can't match by headers predicate 'containsValue'")
        void should_return_error_when_can_t_match_by_headers_predicate_containsvalue() {
            var sentHeaders = Map.of("header1", List.of("value1", "value2"), "header2", List.of("value3"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().containsValue("some not existing value"))
                            .respondWithStatus(200)
                            .andTextBody("anyBody")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when can't match by headers predicate 'containsPair'")
        void should_return_error_when_can_t_match_by_headers_predicate_containspair() {
            var sentHeaders = Map.of("header1", List.of("value1", "value2"), "header2", List.of("value2", "value3"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().containsPair("header1", "some not existing value"))
                            .respondWithStatus(200)
                            .andTextBody("anyBody")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when empty content-type in request and 'containsPair' specified")
        void should_return_error_when_empty_content_type_in_request_and_containspair_specified() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().containsPair("header1", "some not existing value"))
                            .respondWithStatus(200)
                            .andTextBody("anyBody")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when empty content-type in request and 'containsAllKeys' specified")
        void should_return_error_when_empty_content_type_in_request_and_containsallkeys_specified() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().containsAllKeys(List.of("header1", "header2")))
                            .respondWithStatus(200)
                            .andTextBody("anyBody")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when 'containsAllKeys' specified, but not matches requested headers")
        void should_return_error_when_containsallkeys_specified_but_not_matches_requested_headers() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().containsAllKeys(List.of("header1", "header2")))
                            .respondWithStatus(200)
                            .andTextBody("anyBody")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(),
                                Map.of("header1", List.of("any")),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by headers predicate 'containsAllKeys'")
        void should_successfully_match_by_headers_predicate_containsallkeys() {
            var sentHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2", "value3"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().containsAllKeys(List.of("header1", "header2")))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by headers predicate 'containsPair'")
        void should_successfully_match_by_headers_predicate_containspair() {
            var expectedMatchPair = Map.entry("header2", "value3");
            var sentHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2", "value3"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers()
                                    .containsPair(expectedMatchPair.getKey(), expectedMatchPair.getValue()))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when can't match by headers predicate 'containsPairList'")
        void should_return_error_when_can_t_match_by_headers_predicate_containspairlist() {
            var sentHeaders = Map.of("header1", List.of("value1", "value2"), "header2", List.of("value2", "value3"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match ->
                                    match.headers().containsPairList("header1", List.of("some not existing value")))
                            .respondWithStatus(200)
                            .andTextBody("anyBody")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when empty content-type in request and 'containsPairList' specified")
        void should_return_error_when_empty_content_type_in_request_and_containspairlist_specified() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match ->
                                    match.headers().containsPairList("header1", List.of("some not existing value")))
                            .respondWithStatus(200)
                            .andTextBody("anyBody")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfuly match by headers predicate 'containsPairList'")
        void should_successfuly_match_by_headers_predicate_containspairlist() {
            var expectedMatchPair = Map.entry("header2", List.of("value2", "value3"));
            var sentHeaders =
                    Map.of("header1", List.of("value1"), expectedMatchPair.getKey(), expectedMatchPair.getValue());
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers()
                                    .containsPairList(expectedMatchPair.getKey(), expectedMatchPair.getValue()))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by headers predicate 'hasContentType'")
        void should_successfully_match_by_headers_predicate_hascontenttype() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().hasContentType("text/plain"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(),
                                Map.of("content-type", List.of("text/plain")),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when 'hasContentType' doesn't match")
        void should_return_error_when_hascontenttype_doesn_t_match() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().hasContentType("application/json"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when 'hasContentType' specified, but contentType is null in request")
        void should_return_error_when_hascontenttype_specified_but_contenttype_is_null_in_request() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().hasContentType("application/json"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andExactHeaders(Map.of()))
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when provided non-json request and try to match by jsonPath")
        void should_return_error_when_provided_non_json_request_and_try_to_match_by_jsonpath() {
            ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.[0]").isPresent())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort()
                    .useServer(server -> {
                        var response = sendHttpRequestWithBody(
                                        server.port(), "{\"key : val}", HttpResponse.BodyHandlers.ofString())
                                .join();

                        expectSelfie(responseToString(response)).toMatchDisk();
                    });
        }

        @Test
        @DisplayName(
                "should return fallback when 'hasContentType' specified, but contentType is null in request and fallback specified")
        void
                should_return_fallback_when_hascontenttype_specified_but_contenttype_is_null_in_request_and_fallback_specified() {
            var fallbackStatus = ImpHttpStatus.BAD_REQUEST;
            var fallbackBody = "fallback";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().hasContentType("application/json"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andExactHeaders(Map.of()))
                    .fallbackForNonMatching(builder -> builder.status(fallbackStatus.value())
                            .body(() -> new ByteArrayInputStream(fallbackBody.getBytes(StandardCharsets.UTF_8))))
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName(
                "should return fallback when 'hasContentType' specified, but contentType is empty list in request and fallback specified")
        void
                should_return_fallback_when_hascontenttype_specified_but_contenttype_is_empty_list_in_request_and_fallback_specified() {
            var fallbackStatus = ImpHttpStatus.BAD_REQUEST;
            var fallbackBody = "fallback";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().hasContentType("application/json"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andExactHeaders(Map.of()))
                    .fallbackForNonMatching(builder -> builder.status(fallbackStatus.value())
                            .body(() -> new ByteArrayInputStream(fallbackBody.getBytes(StandardCharsets.UTF_8)))
                            .headers(Map.of("headerKey", List.of("headerValue1", "headerValue2"))))
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(),
                                Map.of("Content-Type", List.of()),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName(
                "should return fallback when 'hasContentType' specified, request contains content-type header, but content-type not matches and fallback specified")
        void
                should_return_fallback_when_hascontenttype_specified_request_contains_content_type_header_but_content_type_not_matches_and_fallback_specified() {
            var fallbackStatus = ImpHttpStatus.BAD_REQUEST;
            var fallbackBody = "fallback";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().hasContentType("application/json"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andExactHeaders(Map.of()))
                    .fallbackForNonMatching(builder -> builder.status(fallbackStatus.value())
                            .body(() -> new ByteArrayInputStream(fallbackBody.getBytes(StandardCharsets.UTF_8))))
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithHeaders(
                                impServer.port(),
                                Map.of("Content-Type", List.of("some", "type")),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("`matchRequest` if closure throws exception then fail immediately")
        void matchrequest_if_closure_throws_exception_then_fail_immediately() {
            var matcherException = new RuntimeException("matcher exception");
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> {
                        throw matcherException;
                    }))
                    .withMessage(matcherException.getMessage());
        }

        @Test
        @DisplayName("should successfully match by body predicate 'bodyContains'")
        void should_successfully_match_by_body_predicate_bodycontains() {
            var requestBody = "Some Text body";
            var expectedMatch = "Text";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.body().contains(expectedMatch))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when don't match by body predicate 'bodyContains'")
        void should_fail_when_don_t_match_by_body_predicate_bodycontains() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.body().contains("test"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @ParameterizedTest
        @DisplayName("should be able to match by all methods`")
        @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH"})
        void should_be_able_to_match_by_all_methods(String method) {
            var requestBody = "Some Text body";
            @SuppressWarnings("MagicConstant")
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().is(method))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method(method, HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk(method);
            });
        }

        @Test
        @DisplayName("should be able to match by `anyOf PUT GET`")
        void should_be_able_to_match_by_anyof_put_get() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().anyOf("PUT", "GET"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("GET", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when can't match by method 'GET'`")
        void should_fail_when_can_t_match_by_method_get() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().get())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("POST", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when can't match by method 'is GET'`")
        void should_fail_when_can_t_match_by_method_is_get() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().is("POST"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("GET", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when can't match by method 'POST'`")
        void should_fail_when_can_t_match_by_method_post() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().post())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("GET", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when can't match by method 'PATCH'`")
        void should_fail_when_can_t_match_by_method_patch() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().patch())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("GET", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when can't match by method 'OPTIONS'`")
        void should_fail_when_can_t_match_by_method_options() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().options())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("GET", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when can't match by method 'DELETE'`")
        void should_fail_when_can_t_match_by_method_delete() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().delete())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("GET", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when can't match by method 'PUT'`")
        void should_fail_when_can_t_match_by_method_put() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().put())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("GET", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when can't match by method 'HEAD'`")
        void should_fail_when_can_t_match_by_method_head() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().head())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("GET", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when can't match by method 'TRACE'`")
        void should_fail_when_can_t_match_by_method_trace() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().trace())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("GET", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when can't match by method 'anyOf PUT DELETE'`")
        void should_fail_when_can_t_match_by_method_anyof_put_delete() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.method().anyOf("PUT", "DELETE"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", impServer.port())))
                                        .method("GET", HttpRequest.BodyPublishers.ofString(requestBody))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by body predicate 'bodyContainsIgnoreCase'")
        void should_successfully_match_by_body_predicate_bodycontainsignorecase() {
            var requestBody = "Some Text body";
            var expectedMatch = "text";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.body().containsIgnoreCase(expectedMatch))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by body predicate 'bodyMatches'")
        void should_successfully_match_by_body_predicate_bodymatches() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.body().matches(".*ext.*"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when fail to match by body predicate 'bodyMatches'")
        void should_return_error_when_fail_to_match_by_body_predicate_bodymatches() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.body().matches(".*extt.*"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when fail to match by body predicate 'bodyContainsIgnoreCase'")
        void should_fail_when_fail_to_match_by_body_predicate_bodycontainsignorecase() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.body().containsIgnoreCase("texttt"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when fail to match by jsonpath predicate 'stringEquals'")
        void should_fail_when_fail_to_match_by_jsonpath_predicate_stringequals() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.stringKey").stringEquals("not matches"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), jsonFixture(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when fail to match by jsonpath predicate 'isTrue'")
        void should_fail_when_fail_to_match_by_jsonpath_predicate_istrue() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.stringKey").isTrue())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), jsonFixture(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when fail to match by jsonpath predicate 'matches'")
        void should_fail_when_fail_to_match_by_jsonpath_predicate_matches() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.stringKey").matches("not matches"))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), jsonFixture(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when fail to match by jsonpath predicate 'isNull'")
        void should_fail_when_fail_to_match_by_jsonpath_predicate_isnull() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.stringKey").isNull())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), jsonFixture(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when fail to match by jsonpath predicate 'isPresent'")
        void should_fail_when_fail_to_match_by_jsonpath_predicate_ispresent() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.stringKey123").isPresent())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), jsonFixture(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when fail to match by jsonpath predicate 'isNotPresent'")
        void should_fail_when_fail_to_match_by_jsonpath_predicate_isnotpresent() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.stringKey").isNotPresent())
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), jsonFixture(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when fail to match by jsonpath predicate 'numberEquals'")
        void should_fail_when_fail_to_match_by_jsonpath_predicate_numberequals() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.stringKey").numberEquals(123))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), jsonFixture(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when fail to match by jsonpath predicate 'decimalEquals'")
        void should_fail_when_fail_to_match_by_jsonpath_predicate_decimalequals() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.stringKey").decimalEquals(BigDecimal.ONE))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), jsonFixture(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by body and headers together")
        void should_successfully_match_by_body_and_headers_together() {
            var requestBody = "Some Text body";
            var requestHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.and(
                                    match.body().containsIgnoreCase("text"),
                                    match.headers().containsPair("header1", "value1")))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var requestBuilder = HttpRequest.newBuilder(
                                new URI(String.format("http://localhost:%d/", impServer.port())))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                requestHeaders.forEach((key, valList) -> valList.forEach(val -> requestBuilder.header(key, val)));
                var request = requestBuilder.build();

                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when successfully match by body but fail to match by headers")
        void should_fail_when_successfully_match_by_body_but_fail_to_match_by_headers() {
            var requestBody = "Some Text body";
            var requestHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.and(
                                    match.body().containsIgnoreCase("text"),
                                    match.headers().containsPair("header1", "value1qweqwe")))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var requestBuilder = HttpRequest.newBuilder(
                                new URI(String.format("http://localhost:%d/", impServer.port())))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                requestHeaders.forEach((key, valList) -> valList.forEach(val -> requestBuilder.header(key, val)));
                var request = requestBuilder.build();

                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when two matchers failed to match request and no fallback specified")
        void should_return_error_when_two_matchers_failed_to_match_request_and_no_fallback_specified() {
            var requestBody = "Some Text body";
            var requestHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcher1")
                            .priority(0)
                            .match(match -> match.and(
                                    match.body().containsIgnoreCase("text"),
                                    match.headers().containsPair("header1", "value1qweqwe")))
                            .respondWithStatus(200)
                            .andTextBody("response body1")
                            .andNoAdditionalHeaders())
                    .matchRequest(spec -> spec.id("matcher2")
                            .priority(1)
                            .match(match -> match.and(
                                    match.body().containsIgnoreCase("text"),
                                    match.headers().containsPair("header1", "value1qweqwe")))
                            .respondWithStatus(200)
                            .andTextBody("response body2")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var requestBuilder = HttpRequest.newBuilder(
                                new URI(String.format("http://localhost:%d/", impServer.port())))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                requestHeaders.forEach((key, valList) -> valList.forEach(val -> requestBuilder.header(key, val)));
                var request = requestBuilder.build();

                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when successfully match by headers but fail to match by body")
        void should_fail_when_successfully_match_by_headers_but_fail_to_match_by_body() {
            var requestBody = "Some Text body";
            var requestHeaders = Map.of("header1", List.of("value1"), "header2", List.of("value2"));
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.and(
                                    match.body().containsIgnoreCase("texttttt"),
                                    match.headers().containsPair("header1", "value1")))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var requestBuilder = HttpRequest.newBuilder(
                                new URI(String.format("http://localhost:%d/", impServer.port())))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                requestHeaders.forEach((key, valList) -> valList.forEach(val -> requestBuilder.header(key, val)));
                var request = requestBuilder.build();

                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by body predicate 'testBodyString'")
        void should_successfully_match_by_body_predicate_testbodystring() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.body().testBodyString(bodyString -> !bodyString.isEmpty()))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when fail to match by body predicate 'testBodyString'")
        void should_return_error_when_fail_to_match_by_body_predicate_testbodystring() {
            var requestBody = "Some Text body";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.body().testBodyString(String::isEmpty))
                            .respondWithStatus(200)
                            .andTextBody("any")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when `testBodyString` predicate throws exception")
        void should_return_error_when_testbodystring_predicate_throws_exception() {
            var requestBody = "Some Text body";
            var testBodyStringException = new RuntimeException("testBodyString exception");
            var matcherId = "matcherId";
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id(matcherId)
                            .priority(0)
                            .match(match -> match.body().testBodyString(str -> {
                                throw testBodyStringException;
                            }))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when fail to match by path predicate 'matches'")
        void should_return_error_when_fail_to_match_by_path_predicate_matches() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.path().matches(".*local.*"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by path predicate 'matches' at root path")
        void should_successfully_match_by_path_predicate_matches_at_root_path() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.path().matches("/"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by path predicate 'matches' at specific path")
        void should_successfully_match_by_path_predicate_matches_at_specific_path() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.path().matches(".*some/.*"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(
                                                String.format("http://localhost:%d/some/path", impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when fail to match by url predicate 'urlContains' at specific path")
        void should_return_error_when_fail_to_match_by_url_predicate_urlcontains_at_specific_path() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.path().contains("me/pa"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(
                                                String.format("http://localhost:%d/some/Path", impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by url predicate 'urlContains' at specific path")
        void should_successfully_match_by_url_predicate_urlcontains_at_specific_path() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.path().contains("ome/P"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(
                                                String.format("http://localhost:%d/some/Path", impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by url predicate 'urlContainsIgnoreCase' at specific path")
        void should_successfully_match_by_url_predicate_urlcontainsignorecase_at_specific_path() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.path().containsIgnoreCase("ome/p"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(
                                                String.format("http://localhost:%d/some/Path", impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by query predicate 'hasKey'")
        void should_successfully_match_by_query_predicate_haskey() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.query().hasKey("query1"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format(
                                                "http://localhost:%d/some/path?query1=param1&query2=param2&qw",
                                                impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by query predicate 'hasKey' when query value is not present")
        void should_successfully_match_by_query_predicate_haskey_when_query_value_is_not_present() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.query().hasKey("qw"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format(
                                                "http://localhost:%d/some/path?query1=param1&query2=param2&qw",
                                                impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by query predicate 'hasKey' when query value is empty after =")
        void should_successfully_match_by_query_predicate_haskey_when_query_value_is_empty_after() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.query().hasKey("qw"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format(
                                                "http://localhost:%d/some/path?query1=param1&query2=param2&qw=",
                                                impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error response could match by `query hasKey`, but was negated by `not`")
        void should_return_error_response_could_match_by_query_haskey_but_was_negated_by_not() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.not(match.query().hasKey("qw")))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format(
                                                "http://localhost:%d/some/path?query1=param1&query2=param2&qw=",
                                                impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName(
                "should be able to match by query `hasKey or hasKey` when one first match failed and second succeeded")
        void should_be_able_to_match_by_query_haskey_or_haskey_when_one_first_match_failed_and_second_succeeded() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.or(
                                    match.not(match.query().hasKey("qw")),
                                    match.query().hasKey("qw")))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format(
                                                "http://localhost:%d/some/path?query1=param1&query2=param2&qw=",
                                                impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should be able to match by query `hasKey and hasKey` when both matches succeeded")
        void should_be_able_to_match_by_query_haskey_and_haskey_when_both_matches_succeeded() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.and(
                                    match.not(match.query().hasKey("qweeee")),
                                    match.query().hasKey("query2")))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format(
                                                "http://localhost:%d/some/path?query1=param1&query2=param2&qw=",
                                                impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should generate correct error response when has complex nested matchers that did not match")
        void should_generate_correct_error_response_when_has_complex_nested_matchers_that_did_not_match() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.and(
                                    match.or(
                                            match.query().hasKey("qw"),
                                            match.and(match.not(match.path().containsIgnoreCase("me/p")))),
                                    match.not(match.headers().containsValue("whatever")),
                                    match.path().containsIgnoreCase("asdf"),
                                    match.and(match.not(match.path().containsIgnoreCase("asdf"))),
                                    match.jsonPath("$.[0]").isFalse()))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format(
                                                "http://localhost:%d/some/path?query1=param1&query2=param2&qw=",
                                                impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when fail to match by query predicate 'hasKey'")
        void should_return_error_when_fail_to_match_by_query_predicate_haskey() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.query().hasKey("query3"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format(
                                                "http://localhost:%d/some/path?query1=param1&query2=param2&qw",
                                                impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when query is empty but request to match by query predicate 'hasKey'")
        void should_return_error_when_query_is_empty_but_request_to_match_by_query_predicate_haskey() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.query().hasKey("query1"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(
                                                String.format("http://localhost:%d/some/path", impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by query predicate 'hasParam'")
        void should_successfully_match_by_query_predicate_hasparam() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.query().hasParam("query1", "param1"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format(
                                                "http://localhost:%d/some/path?query1=param1&query2=param2&qw",
                                                impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when fail to match by query predicate 'hasParam'")
        void should_return_error_when_fail_to_match_by_query_predicate_hasparam() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.query().hasParam("query1", "param2"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(String.format(
                                                "http://localhost:%d/some/path?query1=param1&query2=param2&qw",
                                                impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when query is empty but request to match by query predicate 'hasParam'")
        void should_return_error_when_query_is_empty_but_request_to_match_by_query_predicate_hasparam() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.query().hasParam("query1", "param2"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(
                                                String.format("http://localhost:%d/some/path", impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when fail to match by url predicate 'urlContainsIgnoreCase' at specific path")
        void should_return_error_when_fail_to_match_by_url_predicate_urlcontainsignorecase_at_specific_path() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.path().containsIgnoreCase("ome/ppp"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(
                                                String.format("http://localhost:%d/some/Path", impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should fail when match success match by path predicate but fail to match by body")
        void should_fail_when_match_success_match_by_path_predicate_but_fail_to_match_by_body() {
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.and(
                                    match.path().matches(".*some/.*"),
                                    match.body().contains("text")))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequest(
                                HttpRequest.newBuilder(new URI(
                                                String.format("http://localhost:%d/some/path", impServer.port())))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should successfully match by body predicate 'jsonPath' 'stringEquals'")
        void should_successfully_match_by_body_predicate_jsonpath_stringequals() {
            @Language("json")
            var requestBody =
                    """
                        {
                          "key": "val"
                        }
                        """;
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.key").stringEquals("val"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @DisplayName("should return error when fail to match by body predicate 'jsonPath' 'stringEquals'")
        void should_return_error_when_fail_to_match_by_body_predicate_jsonpath_stringequals() {
            @Language("json")
            var requestBody =
                    """
                        {
                          "key": "val"
                        }
                        """;
            var subject = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.jsonPath("$.key").stringEquals("val2"))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort();

            subject.useServer(impServer -> {
                var response = sendHttpRequestWithBody(
                                impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            });
        }

        @Test
        @SuppressWarnings("LanguageMismatch")
        @DisplayName(
                "should fail immediately when incorrect jsonpath provided for match by body predicate 'jsonPath' 'stringEquals'")
        void
                should_fail_immediately_when_incorrect_jsonpath_provided_for_match_by_body_predicate_jsonpath_stringequals() {
            @Language("text")
            var jsonPath = "$.ke][]y";
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.jsonPath(jsonPath).stringEquals("val2"))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .withMessage(
                            "Provided invalid JsonPath - [ %s ]. Check internal error message for details", jsonPath)
                    .withCauseInstanceOf(InvalidPathException.class);
        }

        @Test
        @DisplayName("should be able to build response based on request body with `andBodyBasedOnRequest`")
        void should_be_able_to_build_response_based_on_request_body_with_andbodybasedonrequest() {
            ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(1)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andBodyBasedOnRequest(
                                    "text/plain",
                                    request -> () -> new ByteArrayInputStream(
                                            request.body().getBytes(StandardCharsets.UTF_8)))
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort()
                    .useServer(impServer -> {
                        var requestBody = "request body";
                        var response = sendHttpRequestWithBody(
                                        impServer.port(), requestBody, HttpResponse.BodyHandlers.ofString())
                                .join();

                        expectSelfie(responseToString(response)).toMatchDisk();
                    });
        }

        @Test
        @DisplayName(
                "should take matcher with lowest priority value when multiple matchers matched request and matcher is last in list")
        void
                should_take_matcher_with_lowest_priority_value_when_multiple_matchers_matched_request_and_matcher_is_last_in_list() {
            var expectedResponseBody = "response body";
            ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId1")
                            .priority(2)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody("should not be matched")
                            .andNoAdditionalHeaders())
                    .matchRequest(spec -> spec.id("matcherId2")
                            .priority(1)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody(expectedResponseBody)
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort()
                    .useServer(impServer -> {
                        var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                                .join();

                        expectSelfie(responseToString(response)).toMatchDisk();
                    });
        }

        @Test
        @DisplayName("should fail immediately when trying to add two matchers with same id")
        void should_fail_immediately_when_trying_to_add_two_matchers_with_same_id() {
            var matcherId = "matcherId";
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .matchRequest(spec -> spec.id(matcherId)
                                    .priority(2)
                                    .match(ImpMatch::everything)
                                    .respondWithStatus(200)
                                    .andTextBody("")
                                    .andNoAdditionalHeaders())
                            .matchRequest(spec -> spec.id(matcherId)
                                    .priority(1)
                                    .match(ImpMatch::everything)
                                    .respondWithStatus(200)
                                    .andTextBody("")
                                    .andNoAdditionalHeaders()))
                    .withMessage(
                            "Duplicated matcher id detected: [%s]. Consider using unique id for each matcher. "
                                    + "Currently known matcher ids: [%s]",
                            matcherId, matcherId);
        }

        @Test
        @DisplayName("should fail immediately when trying to add two matchers with same priority")
        void should_fail_immediately_when_trying_to_add_two_matchers_with_same_priority() {
            var matcherPriority = 1;
            var matcherId1 = "matcherId1";
            var matcherId2 = "matcherId2";
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .matchRequest(spec -> spec.id(matcherId1)
                                    .priority(matcherPriority)
                                    .match(ImpMatch::everything)
                                    .respondWithStatus(200)
                                    .andTextBody("")
                                    .andNoAdditionalHeaders())
                            .matchRequest(spec -> spec.id(matcherId2)
                                    .priority(matcherPriority)
                                    .match(ImpMatch::everything)
                                    .respondWithStatus(200)
                                    .andTextBody("")
                                    .andNoAdditionalHeaders()))
                    .withMessage(
                            "Duplicated matcher priority detected: trying to set priority [%d] for matcher [%s], "
                                    + "but is already set for matcher [%s]. Using same priority for different matchers can lead to unexpected, "
                                    + "non-deterministic behavior. Consider using unique priority for each matcher.",
                            matcherPriority, matcherId2, matcherId1);
        }

        @Test
        @DisplayName(
                "should take matcher with lowest priority value when multiple matchers matched request and matcher is first in list")
        void
                should_take_matcher_with_lowest_priority_value_when_multiple_matchers_matched_request_and_matcher_is_first_in_list() {
            var expectedResponseBody = "response body";
            ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("matcherId1")
                            .priority(1)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody(expectedResponseBody)
                            .andNoAdditionalHeaders())
                    .matchRequest(spec -> spec.id("matcherId2")
                            .priority(2)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody("should not be matched")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .onRandomPort()
                    .useServer(impServer -> {
                        var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                                .join();

                        expectSelfie(responseToString(response)).toMatchDisk();
                    });
        }
    }

    @Nested
    class SharedServerSuite implements FastTest {

        @Test
        @DisplayName("when shared server is running, isDisposed should return true")
        void when_shared_server_is_running_isdisposed_should_return_true() {
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody("any").andNoAdditionalHeaders())
                    .startSharedOnRandomPort();
            try {
                assertThat(sharedServer.isDisposed()).isFalse();
                assertThat(sharedServer.isDisposed()).isFalse();
                assertThat(sharedServer.isDisposed()).isFalse();
            } finally {
                sharedServer.dispose();
            }
        }

        @Test
        @DisplayName("when shared server is stopped, isDisposed should return false")
        void when_shared_server_is_stopped_isdisposed_should_return_false() {
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody("any").andNoAdditionalHeaders())
                    .startSharedOnRandomPort();

            sharedServer.dispose();

            assertThat(sharedServer.isDisposed()).isTrue();
            assertThat(sharedServer.isDisposed()).isTrue();
            assertThat(sharedServer.isDisposed()).isTrue();
        }

        @Test
        @DisplayName("should be able to send multiple requests to shared server")
        void should_be_able_to_send_multiple_requests_to_shared_server() {
            var body = "some text";
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody(body).andNoAdditionalHeaders())
                    .startSharedOnRandomPort();

            ImpRunnable action = () -> {
                var request = HttpRequest.newBuilder(
                                new URI(String.format("http://localhost:%d/", sharedServer.port())))
                        .build();
                var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                        .join();

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
        @DisplayName("when shared server is stopped, then dont accept further requests")
        void when_shared_server_is_stopped_then_dont_accept_further_requests() throws Exception {
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(spec ->
                            spec.withStatus(200).andTextBody("some text").andNoAdditionalHeaders())
                    .startSharedOnRandomPort();
            sharedServer.dispose();

            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", sharedServer.port())))
                    .build();
            assertThatExceptionOfType(CompletionException.class)
                    .isThrownBy(() -> sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                            .join())
                    .withCauseInstanceOf(ConnectException.class);
        }

        @Test
        @DisplayName("when try to start server with `startSharedOnPort` but port already taken, then fail immediately")
        void when_try_to_start_server_with_startsharedonport_but_port_already_taken_then_fail_immediately() {
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(spec ->
                            spec.withStatus(200).andTextBody("some text").andNoAdditionalHeaders())
                    .startSharedOnRandomPort();

            try {
                var specEnd = ImpServer.httpTemplate()
                        .alwaysRespond(spec ->
                                spec.withStatus(200).andTextBody("some text").andNoAdditionalHeaders());
                var port = sharedServer.port();

                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(() -> specEnd.startSharedOnPort(port))
                        .withMessage("Could not acquire port [%d] after [5] retries - port is in use", port);
            } finally {
                sharedServer.dispose();
            }
        }

        @Test
        @DisplayName("should be able to start shared server on specific port when it is not taken")
        void should_be_able_to_start_shared_server_on_specific_port_when_it_is_not_taken() {
            var port = randomPort();
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(spec ->
                            spec.withStatus(200).andTextBody("some text").andNoAdditionalHeaders())
                    .startSharedOnPort(port);
            try {
                var response = sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            } finally {
                sharedServer.dispose();
            }
        }

        @Test
        @DisplayName("`andExactHeaders` should replace existing headers and possibly add new")
        void andexactheaders_should_replace_existing_headers_and_possibly_add_new() {
            var exactHeaders =
                    Map.of("content-type", List.of("application/json"), "another-header", List.of("some value"));
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(spec ->
                            spec.withStatus(200).andTextBody("some text").andExactHeaders(exactHeaders))
                    .startSharedOnRandomPort();
            try {
                var response = sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            } finally {
                sharedServer.dispose();
            }
        }

        @Test
        @DisplayName("`andAdditionalHeaders` should add new but not change exiting headers")
        void andadditionalheaders_should_add_new_but_not_change_exiting_headers() {
            var additionalHeaders =
                    Map.of("content-type", List.of("application/json"), "another-header", List.of("some value"));
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(spec ->
                            spec.withStatus(200).andTextBody("some text").andAdditionalHeaders(additionalHeaders))
                    .startSharedOnRandomPort();
            try {
                var response = sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            } finally {
                sharedServer.dispose();
            }
        }

        @Test
        @DisplayName("when shared server is stopped many times, then no exception thrown and server is still stopped")
        void when_shared_server_is_stopped_many_times_then_no_exception_thrown_and_server_is_still_stopped()
                throws Exception {
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(spec ->
                            spec.withStatus(200).andTextBody("some text").andNoAdditionalHeaders())
                    .startSharedOnRandomPort();
            assertThat(sharedServer.isDisposed()).isFalse();
            sharedServer.dispose();
            assertThat(sharedServer.isDisposed()).isTrue();
            sharedServer.dispose();
            assertThat(sharedServer.isDisposed()).isTrue();
            sharedServer.dispose();
            assertThat(sharedServer.isDisposed()).isTrue();
            sharedServer.dispose();
            assertThat(sharedServer.isDisposed()).isTrue();

            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", sharedServer.port())))
                    .build();
            assertThatExceptionOfType(CompletionException.class)
                    .isThrownBy(() -> sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                            .join())
                    .withCauseInstanceOf(ConnectException.class);
        }

        @Test
        @DisplayName("should be able to start shared server with matchers on specific port")
        void should_be_able_to_start_shared_server_with_matchers_on_specific_port() {
            var expectedMatchValue = "value2";
            var sentHeaders = Map.of("header1", List.of("value1", expectedMatchValue));
            var port = randomPort();
            var sharedServer = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().containsValue(expectedMatchValue))
                            .respondWithStatus(200)
                            .andTextBody("response body")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .startSharedOnPort(port);
            try {
                var response = sendHttpRequestWithHeaders(
                                sharedServer.port(), sentHeaders, HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();

            } finally {
                sharedServer.dispose();
            }
        }
    }

    @Nested
    class BorrowedSuite implements FastTest {

        @Test
        @DisplayName("should match request by header key using containsKey matcher in borrowed server")
        void should_match_request_by_header_key_using_containskey_matcher_in_borrowed_server() {
            useDefaultSharedServer(sharedServer -> {
                var matcherId = "matcherId";
                var expectedBody = "matched by header key";
                var expectedStatus = 201;

                var impBorrowed = sharedServer
                        .borrow()
                        .matchRequest(spec -> spec.id(matcherId)
                                .priority(0)
                                .match(match -> match.headers().containsKey("user-agent"))
                                .respondWithStatus(expectedStatus)
                                .andTextBody(expectedBody)
                                .andNoAdditionalHeaders())
                        .rejectNonMatching();

                impBorrowed.useServer(server -> {
                    var response = sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString())
                            .join();

                    expectSelfie(responseToString(response)).toMatchDisk();
                });
            });
        }

        @Test
        @DisplayName("should return fallback response when no matchers match the request in borrowed server")
        void should_return_fallback_response_when_no_matchers_match_the_request_in_borrowed_server() {
            useDefaultSharedServer(sharedServer -> {
                var matcherId = "matcherId";
                var fallbackBody = "fallback response";
                var fallbackStatus = 404;

                var impBorrowed = sharedServer
                        .borrow()
                        .matchRequest(spec -> spec.id(matcherId)
                                .priority(0)
                                .match(match -> match.headers().containsKey("non-existent-header"))
                                .respondWithStatus(200)
                                .andTextBody("should not be returned")
                                .andNoAdditionalHeaders())
                        .fallbackForNonMatching(spec -> spec.status(fallbackStatus)
                                .body(() -> new ByteArrayInputStream(fallbackBody.getBytes(StandardCharsets.UTF_8))));

                impBorrowed.useServer(server -> {
                    var response = sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString())
                            .join();

                    expectSelfie(responseToString(response)).toMatchDisk();
                });
            });
        }

        @Test
        @DisplayName("should match request by path in borrowed server")
        void should_match_request_by_path_in_borrowed_server() {
            useDefaultSharedServer(sharedServer -> {
                var matcherId = "matcherId";
                var expectedBody = "matched by path";
                var expectedStatus = 201;
                @Language("regexp")
                var pathToMatch = "/api/users";

                var impBorrowed = sharedServer
                        .borrow()
                        .matchRequest(spec -> spec.id(matcherId)
                                .priority(0)
                                .match(match -> match.path().matches(pathToMatch))
                                .respondWithStatus(expectedStatus)
                                .andTextBody(expectedBody)
                                .andNoAdditionalHeaders())
                        .rejectNonMatching();

                impBorrowed.useServer(server -> {
                    var request = HttpRequest.newBuilder(
                                    new URI(String.format("http://localhost:%d%s", server.port(), pathToMatch)))
                            .build();
                    var response = sendHttpRequest(request, HttpResponse.BodyHandlers.ofString())
                            .join();

                    expectSelfie(responseToString(response)).toMatchDisk();
                });
            });
        }

        @Test
        @DisplayName("should prioritize matchers by priority value in borrowed server")
        void should_prioritize_matchers_by_priority_value_in_borrowed_server() {
            useDefaultSharedServer(sharedServer -> {
                var lowPriorityId = "lowPriorityMatcher";
                var highPriorityId = "highPriorityMatcher";
                var lowPriorityBody = "low priority response";
                var highPriorityBody = "high priority response";

                var impBorrowed = sharedServer
                        .borrow()
                        .matchRequest(spec -> spec.id(lowPriorityId)
                                .priority(10)
                                .match(match -> match.headers().containsKey("user-agent"))
                                .respondWithStatus(200)
                                .andTextBody(lowPriorityBody)
                                .andNoAdditionalHeaders())
                        .matchRequest(spec -> spec.id(highPriorityId)
                                .priority(1)
                                .match(match -> match.headers().containsKey("user-agent"))
                                .respondWithStatus(200)
                                .andTextBody(highPriorityBody)
                                .andNoAdditionalHeaders())
                        .rejectNonMatching();

                impBorrowed.useServer(server -> {
                    var response = sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString())
                            .join();

                    expectSelfie(responseToString(response)).toMatchDisk();
                });
            });
        }

        @Test
        @DisplayName("should be able to send request while borrowing server")
        void should_be_able_to_send_request_while_borrowing_server() {
            var body = "some text";
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody(body).andNoAdditionalHeaders())
                    .startSharedOnRandomPort();

            try {
                var borrowedTextBody = "some borrowed text";
                var borrowedStatus = 400;
                var impBorrowed = sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(borrowedStatus)
                        .andTextBody(borrowedTextBody)
                        .andNoAdditionalHeaders());

                impBorrowed.useServer(server -> {
                    var response = sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString())
                            .join();
                    assertThat(response.body()).isEqualTo(borrowedTextBody);
                    assertThat(response.statusCode()).isEqualTo(borrowedStatus);
                });
            } finally {
                sharedServer.dispose();
            }
        }

        @Test
        @DisplayName(
                "should fail fast with exception when try to use borrowed server at the same time as another thread")
        void should_fail_fast_with_exception_when_try_to_use_borrowed_server_at_the_same_time_as_another_thread() {
            var sharedServerResponseFuture = new CompletableFuture<String>();
            var sendRequestFuture = new CompletableFuture<String>();
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(spec -> spec.withStatus(200)
                            .andDataStreamBody(() -> {
                                sendRequestFuture.complete("");
                                sharedServerResponseFuture.get(3, TimeUnit.SECONDS);
                                return new ByteArrayInputStream("some body".getBytes(StandardCharsets.UTF_8));
                            })
                            .andNoAdditionalHeaders())
                    .startSharedOnRandomPort();

            try (var executor = Executors.newSingleThreadExecutor()) {
                var future =
                        executor.submit(() -> sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString())
                                .join());
                try {
                    var impBorrowed = sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(400)
                            .andTextBody("some borrowed text")
                            .andNoAdditionalHeaders());
                    sendRequestFuture.join();
                    assertThatThrownBy(() -> impBorrowed.useServer(server -> {}))
                            .isInstanceOf(IllegalStateException.class)
                            .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
                    sharedServerResponseFuture.complete("");
                } finally {
                    future.cancel(true);
                }
            } finally {
                sharedServer.dispose();
            }
        }

        @Test
        @DisplayName("borrowed server port should be same as original server port")
        void borrowed_server_port_should_be_same_as_original_server_port() {
            useDefaultSharedServer(sharedServer -> {
                var impBorrowed = sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(400)
                        .andTextBody("anyBorrowed")
                        .andNoAdditionalHeaders());

                impBorrowed.useServer(server -> assertThat(server.port()).isEqualTo(sharedServer.port()));
            });
        }

        @Test
        @DisplayName("should throw exception when try to borrow from already stopped shared server")
        void should_throw_exception_when_try_to_borrow_from_already_stopped_shared_server() {
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody("any").andNoAdditionalHeaders())
                    .startSharedOnRandomPort();

            sharedServer.dispose();

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(sharedServer::borrow)
                    .withMessage("Cannot borrow from already stopped server");
        }

        @Test
        @DisplayName("should throw exception when try to start borrowed server, when parent server is already stopped")
        void should_throw_exception_when_try_to_start_borrowed_server_when_parent_server_is_already_stopped() {
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody("any").andNoAdditionalHeaders())
                    .startSharedOnRandomPort();

            var borrowedServer = sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                    .andTextBody("any")
                    .andNoAdditionalHeaders());

            sharedServer.dispose();

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> borrowedServer.useServer(server -> {}))
                    .withMessage("Shared server is already stopped. Cannot use borrowed server anymore.");
        }

        @Test
        @DisplayName("should return to original state after borrowing closure ends")
        void should_return_to_original_state_after_borrowing_closure_ends() {
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(spec ->
                            spec.withStatus(200).andTextBody("some text").andNoAdditionalHeaders())
                    .startSharedOnRandomPort();

            try {
                var borrowedTextBody = "some borrowed text";
                var borrowedStatus = 400;
                var impBorrowed = sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(borrowedStatus)
                        .andTextBody(borrowedTextBody)
                        .andNoAdditionalHeaders());
                impBorrowed.useServer(server -> sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString())
                        .join());

                var response = sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString())
                        .join();

                expectSelfie(responseToString(response)).toMatchDisk();
            } finally {
                sharedServer.dispose();
            }
        }

        @Test
        @DisplayName("isDisposed should return false when running inside borrowed server")
        void isdisposed_should_return_false_when_running_inside_borrowed_server() {
            useDefaultSharedServer(sharedServer -> sharedServer
                    .borrow()
                    .alwaysRespond(
                            spec -> spec.withStatus(200).andTextBody("any").andNoAdditionalHeaders())
                    .useServer(server -> {
                        assertThat(sharedServer.isDisposed()).isFalse();
                        assertThat(sharedServer.isDisposed()).isFalse();
                    }));
        }

        @Test
        @DisplayName("isDisposed should return false after running borrowed server")
        void isdisposed_should_return_false_after_running_borrowed_server() {
            useDefaultSharedServer(sharedServer -> {
                sharedServer
                        .borrow()
                        .alwaysRespond(
                                spec -> spec.withStatus(200).andTextBody("any").andNoAdditionalHeaders())
                        .useServer(server -> {});
                assertThat(sharedServer.isDisposed()).isFalse();
            });
        }

        @Test
        @DisplayName("borrowed server should reset hits and misses inside closure")
        void borrowed_server_should_reset_hits_and_misses_inside_closure() {
            var originalBody = "some text";
            var originalStatus = 200;
            var expectedHeader = "some-header";
            var headers = Map.of(expectedHeader, List.of("some-value"));
            var sharedServer = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().containsKey(expectedHeader))
                            .respondWithStatus(originalStatus)
                            .andTextBody(originalBody)
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .startSharedOnRandomPort();

            sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString())
                    .join();
            sendHttpRequestWithHeaders(sharedServer.port(), headers, HttpResponse.BodyHandlers.ofString())
                    .join();
            try {
                var borrowedTextBody = "some borrowed text";
                var borrowedStatus = 400;
                var impBorrowed = sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(borrowedStatus)
                        .andTextBody(borrowedTextBody)
                        .andNoAdditionalHeaders());
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
            var sharedServer = ImpServer.httpTemplate()
                    .matchRequest(spec -> spec.id("anyId")
                            .priority(0)
                            .match(match -> match.headers().containsKey(expectedHeader))
                            .respondWithStatus(originalStatus)
                            .andTextBody(originalBody)
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .startSharedOnRandomPort();

            sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString())
                    .join();
            sendHttpRequestWithHeaders(sharedServer.port(), headers, HttpResponse.BodyHandlers.ofString())
                    .join();
            try {
                sharedServer
                        .borrow()
                        .alwaysRespond(spec -> spec.withStatus(400)
                                .andTextBody("some borrowed text")
                                .andNoAdditionalHeaders())
                        .useServer(server -> {
                            sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();
                            sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();
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
            useDefaultSharedServer(sharedServer -> {
                var statistics = sharedServer
                        .borrow()
                        .alwaysRespond(spec -> spec.withStatus(400)
                                .andTextBody("some borrowed text")
                                .andNoAdditionalHeaders())
                        .useServer(server -> {
                            assertThat(server.statistics().hitCount()).isZero();
                            sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();
                            assertThat(server.statistics().hitCount()).isOne();
                            sendHttpRequest(server.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();
                            assertThat(server.statistics().hitCount()).isEqualTo(2);
                            assertThat(server.statistics().missCount()).isZero();
                        });
                assertThat(statistics.hitCount()).isEqualTo(2);
                assertThat(statistics.missCount()).isZero();
            });
        }

        @Test
        @DisplayName("`andTextBody` should fail immediately on borrowed server when pass null")
        void andtextbody_should_fail_immediately_on_borrowed_server_when_pass_null() {
            useDefaultSharedServer(sharedServer -> {
                //noinspection DataFlowIssue
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                                .andTextBody(null)
                                .andNoAdditionalHeaders()))
                        .withMessage("nulls are not supported - textBody");
            });
        }

        @Test
        @DisplayName("`andJsonBody` should fail immediately on borrowed server when pass null")
        void andjsonbody_should_fail_immediately_on_borrowed_server_when_pass_null() {
            useDefaultSharedServer(sharedServer -> {
                //noinspection DataFlowIssue
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                                .andJsonBody(null)
                                .andNoAdditionalHeaders()))
                        .withMessage("nulls are not supported - jsonBody");
            });
        }

        @Test
        @DisplayName("`andXmlBody` should fail immediately on borrowed server when pass null")
        void andxmlbody_should_fail_immediately_on_borrowed_server_when_pass_null() {
            useDefaultSharedServer(sharedServer -> {
                //noinspection DataFlowIssue
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                                .andXmlBody(null)
                                .andNoAdditionalHeaders()))
                        .withMessage("nulls are not supported - xmlBody");
            });
        }

        @Test
        @DisplayName("`andDataStreamBody` should fail immediately on borrowed server when pass null")
        void anddatastreambody_should_fail_immediately_on_borrowed_server_when_pass_null() {
            useDefaultSharedServer(sharedServer -> {
                //noinspection DataFlowIssue
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                                .andDataStreamBody(null)
                                .andNoAdditionalHeaders()))
                        .withMessage("nulls are not supported - dataStreamSupplier");
            });
        }

        @Test
        @DisplayName("`andCustomContentTypeStream` should fail immediately on borrowed server when pass nulls")
        void andcustomcontenttypestream_should_fail_immediately_on_borrowed_server_when_pass_nulls() {
            useDefaultSharedServer(sharedServer -> {
                //noinspection DataFlowIssue
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                                .andCustomContentTypeStream(null, null)
                                .andNoAdditionalHeaders()))
                        .withMessage("null or blank strings are not supported - contentType");
            });
        }

        @Test
        @DisplayName(
                "`andCustomContentTypeStream` should fail immediately on borrowed server when pass null contentType")
        void andcustomcontenttypestream_should_fail_immediately_on_borrowed_server_when_pass_null_contenttype() {
            useDefaultSharedServer(sharedServer -> {
                //noinspection DataFlowIssue
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                                .andCustomContentTypeStream(null, () -> new ByteArrayInputStream(new byte[0]))
                                .andNoAdditionalHeaders()))
                        .withMessage("null or blank strings are not supported - contentType");
            });
        }

        @Test
        @DisplayName(
                "`andCustomContentTypeStream` should fail immediately on borrowed server when pass empty contentType")
        void andcustomcontenttypestream_should_fail_immediately_on_borrowed_server_when_pass_empty_contenttype() {
            useDefaultSharedServer(sharedServer -> assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                            .andCustomContentTypeStream("", () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType"));
        }

        @Test
        @DisplayName(
                "`andCustomContentTypeStream` should fail immediately on borrowed server when pass blank contentType")
        void andcustomcontenttypestream_should_fail_immediately_on_borrowed_server_when_pass_blank_contenttype() {
            useDefaultSharedServer(sharedServer -> assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                            .andCustomContentTypeStream("  ", () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType"));
        }

        @Test
        @DisplayName("`andCustomContentTypeStream` should fail immediately on borrowed server when pass null stream")
        void andcustomcontenttypestream_should_fail_immediately_on_borrowed_server_when_pass_null_stream() {
            useDefaultSharedServer(sharedServer -> {
                //noinspection DataFlowIssue
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                                .andCustomContentTypeStream("any", null)
                                .andNoAdditionalHeaders()))
                        .withMessage("nulls are not supported - dataStreamSupplier");
            });
        }

        @Test
        @DisplayName("`andHeaders` should fail immediately on borrowed server when pass null map")
        void andheaders_should_fail_immediately_on_borrowed_server_when_pass_null_map() {
            useDefaultSharedServer(sharedServer -> {
                //noinspection DataFlowIssue
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                                .andTextBody("")
                                .andExactHeaders(null)))
                        .withMessage("nulls are not supported - headers");
            });
        }

        @Test
        @DisplayName("`andHeaders` should overwrite existing headers")
        void andheaders_should_overwrite_existing_headers() {
            useDefaultSharedServer(sharedServer -> {
                var newHeaders = Map.of(
                        "some-header", List.of("some-value1", "some-value2"), "Content-Type", List.of("some-value3"));
                var newBody = "any";
                var newStatus = 200;
                sharedServer
                        .borrow()
                        .alwaysRespond(spec ->
                                spec.withStatus(newStatus).andTextBody(newBody).andExactHeaders(newHeaders))
                        .useServer(borrowedServer -> {
                            var response = sendHttpRequest(borrowedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();

                            expectSelfie(responseToString(response)).toMatchDisk();
                        });
            });
        }

        @Test
        @DisplayName("`andNoAdditionalHeaders` should not change existing headers")
        void andnoadditionalheaders_should_not_change_existing_headers() {
            useDefaultSharedServer(sharedServer -> {
                var newBody = "any";
                var newStatus = 200;
                sharedServer
                        .borrow()
                        .alwaysRespond(spec ->
                                spec.withStatus(newStatus).andTextBody(newBody).andNoAdditionalHeaders())
                        .useServer(borrowedServer -> {
                            var response = sendHttpRequest(borrowedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();

                            expectSelfie(responseToString(response)).toMatchDisk();
                        });
            });
        }

        @Test
        @DisplayName("`andHeaders` should fail immediately on borrowed server when pass map with null key")
        void andheaders_should_fail_immediately_on_borrowed_server_when_pass_map_with_null_key() {
            var map = new HashMap<String, List<String>>();
            map.put("key", List.of("val1"));
            map.put(null, List.of("val2"));
            useDefaultSharedServer(sharedServer -> assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                            .andTextBody("")
                            .andExactHeaders(map)))
                    .withMessage("null key are not supported in headers, but found null key in entry [ null=[val2] ]"));
        }

        @Test
        @DisplayName("`andHeaders` should fail immediately on borrowed server when pass map with null value")
        void andheaders_should_fail_immediately_on_borrowed_server_when_pass_map_with_null_value() {
            var map = new HashMap<String, List<String>>();
            map.put("key1", List.of("val1"));
            map.put("key2", null);
            useDefaultSharedServer(sharedServer -> assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                            .andTextBody("")
                            .andExactHeaders(map)))
                    .withMessage(
                            "null values are not supported in headers, but found null values in entry [ key2=null ]"));
        }

        @Test
        @DisplayName("`andHeaders` should fail immediately on borrowed server when pass map with null entry")
        void andheaders_should_fail_immediately_on_borrowed_server_when_pass_map_with_null_entry() {
            var map = new HashMap<String, List<String>>();
            map.put("key1", List.of("val1"));
            map.put(null, null);
            useDefaultSharedServer(sharedServer -> assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                            .andTextBody("")
                            .andExactHeaders(map)))
                    .withMessage("null key are not supported in headers, but found null key in entry [ null=null ]"));
        }

        @Test
        @DisplayName(
                "`andHeaders` should fail immediately on borrowed server when pass map with one value in value is null")
        void andheaders_should_fail_immediately_on_borrowed_server_when_pass_map_with_one_value_in_value_is_null() {
            var map = new HashMap<String, List<String>>();
            var valueList = new ArrayList<String>();
            valueList.add("val1");
            valueList.add(null);
            map.put("key1", List.of("val1"));
            map.put("key2", valueList);
            useDefaultSharedServer(
                    sharedServer -> assertThatExceptionOfType(IllegalArgumentException.class)
                            .isThrownBy(() -> sharedServer.borrow().alwaysRespond(spec -> spec.withStatus(200)
                                    .andTextBody("")
                                    .andExactHeaders(map)))
                            .withMessage(
                                    "null values are not supported in headers, but found null values in entry [ key2=[val1, null] ]"));
        }

        @ParameterizedTest
        @ValueSource(ints = {200, 300, 400, 500})
        @DisplayName("borrowed server should always respond with asked status")
        void borrowed_server_should_always_respond_with_asked_status(int expectedStatus) {
            var expectedBody = "some body";
            useDefaultSharedServer(sharedServer -> sharedServer
                    .borrow()
                    .alwaysRespond(spec -> spec.withStatus(expectedStatus)
                            .andTextBody(expectedBody)
                            .andNoAdditionalHeaders())
                    .useServer(borrowedServer -> {
                        var response = sendHttpRequest(borrowedServer.port(), HttpResponse.BodyHandlers.ofString())
                                .join();

                        assertThat(response.body()).isEqualTo(expectedBody);
                        assertThat(response.statusCode()).isEqualTo(expectedStatus);
                    }));
        }

        @Test
        @DisplayName("`andJsonBody` for borrowed server should return json body")
        void andjsonbody_for_borrowed_server_should_return_json_body() {
            useDefaultSharedServer(sharedServer -> {
                @Language("json")
                var newBody = "{}";
                var newStatus = 200;
                sharedServer
                        .borrow()
                        .alwaysRespond(spec ->
                                spec.withStatus(newStatus).andJsonBody(newBody).andNoAdditionalHeaders())
                        .useServer(borrowedServer -> {
                            var response = sendHttpRequest(borrowedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();

                            expectSelfie(responseToString(response)).toMatchDisk();
                        });
            });
        }

        @Test
        @DisplayName("`andXmlBody` for borrowed server should return xml body")
        void andxmlbody_for_borrowed_server_should_return_xml_body() {
            useDefaultSharedServer(sharedServer -> {
                @Language("xml")
                var newBody = "<root></root>";
                var newStatus = 200;
                sharedServer
                        .borrow()
                        .alwaysRespond(spec ->
                                spec.withStatus(newStatus).andXmlBody(newBody).andNoAdditionalHeaders())
                        .useServer(borrowedServer -> {
                            var response = sendHttpRequest(borrowedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();

                            expectSelfie(responseToString(response)).toMatchDisk();
                        });
            });
        }

        @Test
        @DisplayName("`andDataStreamBody` for borrowed server should return octet stream body")
        void anddatastreambody_for_borrowed_server_should_return_octet_stream_body() {
            useDefaultSharedServer(sharedServer -> {
                var newBody = "some body";
                var newStatus = 200;
                sharedServer
                        .borrow()
                        .alwaysRespond(spec -> spec.withStatus(newStatus)
                                .andDataStreamBody(
                                        () -> new ByteArrayInputStream(newBody.getBytes(StandardCharsets.UTF_8)))
                                .andNoAdditionalHeaders())
                        .useServer(borrowedServer -> {
                            var response = sendHttpRequest(borrowedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();

                            expectSelfie(responseToString(response)).toMatchDisk();
                        });
            });
        }

        @Test
        @DisplayName("`andCustomContentTypeStream` for borrowed server should return requested content type and body")
        void andcustomcontenttypestream_for_borrowed_server_should_return_requested_content_type_and_body() {
            useDefaultSharedServer(sharedServer -> {
                var newBody = "some body";
                var newStatus = 200;
                var newContentType = "ctype";
                sharedServer
                        .borrow()
                        .alwaysRespond(spec -> spec.withStatus(newStatus)
                                .andCustomContentTypeStream(
                                        newContentType,
                                        () -> new ByteArrayInputStream(newBody.getBytes(StandardCharsets.UTF_8)))
                                .andNoAdditionalHeaders())
                        .useServer(borrowedServer -> {
                            var response = sendHttpRequest(borrowedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();

                            expectSelfie(responseToString(response)).toMatchDisk();
                        });
            });
        }

        @Test
        @DisplayName(
                "`andCustomContentTypeStream` when provided supplier fails then should return fallback 418 response")
        void andcustomcontenttypestream_when_provided_supplier_fails_then_should_return_fallback_418_response() {
            useDefaultSharedServer(sharedServer -> {
                var newStatus = 200;
                var newContentType = "ctype";
                var dataSupplierException = new RuntimeException("some message");
                sharedServer
                        .borrow()
                        .alwaysRespond(spec -> spec.withStatus(newStatus)
                                .andCustomContentTypeStream(newContentType, () -> {
                                    throw dataSupplierException;
                                })
                                .andNoAdditionalHeaders())
                        .useServer(borrowedServer -> {
                            var response = sendHttpRequest(borrowedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();

                            expectSelfie(responseToString(response)).toMatchDisk();
                        });
            });
        }

        @Test
        @DisplayName("`andDataStreamBody` when provided supplier fails then should return fallback 418 response")
        void anddatastreambody_when_provided_supplier_fails_then_should_return_fallback_418_response() {
            useDefaultSharedServer(sharedServer -> {
                var newStatus = 200;
                var dataSupplierException = new RuntimeException("some message");
                sharedServer
                        .borrow()
                        .alwaysRespond(spec -> spec.withStatus(newStatus)
                                .andDataStreamBody(() -> {
                                    throw dataSupplierException;
                                })
                                .andNoAdditionalHeaders())
                        .useServer(borrowedServer -> {
                            var response = sendHttpRequest(borrowedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();

                            expectSelfie(responseToString(response)).toMatchDisk();
                        });
            });
        }

        @Test
        @DisplayName("should be able to borrow server from shared server that was started on specific port")
        void should_be_able_to_borrow_server_from_shared_server_that_was_started_on_specific_port() {
            var port = randomPort();
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(spec ->
                            spec.withStatus(200).andTextBody("some text").andNoAdditionalHeaders())
                    .startSharedOnPort(port);
            try {
                sharedServer
                        .borrow()
                        .alwaysRespond(spec ->
                                spec.withStatus(400).andTextBody("changed text").andNoAdditionalHeaders())
                        .useServer(borrowedServer -> {
                            assertThat(borrowedServer.port()).isEqualTo(port);
                            var response = sendHttpRequest(sharedServer.port(), HttpResponse.BodyHandlers.ofString())
                                    .join();

                            expectSelfie(responseToString(response)).toMatchDisk();
                        });
                assertThat(sharedServer.statistics().hitCount()).isZero();
                assertThat(sharedServer.statistics().missCount()).isZero();
                assertThat(sharedServer.isDisposed()).isFalse();
            } finally {
                sharedServer.dispose();
            }
        }

        @Test
        @DisplayName("when match everything then return response")
        void when_match_everything_then_return_response() {
            useDefaultSharedServer(sharedServer -> sharedServer
                    .borrow()
                    .matchRequest(spec -> spec.id("matcherId")
                            .priority(1)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody("some text")
                            .andNoAdditionalHeaders())
                    .rejectNonMatching()
                    .useServer(impServer -> {
                        var response = sendHttpRequest(impServer.port(), HttpResponse.BodyHandlers.ofString())
                                .join();

                        expectSelfie(responseToString(response)).toMatchDisk();
                    }));
        }

        void useDefaultSharedServer(ThrowingConsumer<ImpShared> consumer) {
            var originalBody = "some text";
            int originalStatus = 200;
            var sharedServer = ImpServer.httpTemplate()
                    .alwaysRespond(spec -> spec.withStatus(originalStatus)
                            .andTextBody(originalBody)
                            .andNoAdditionalHeaders())
                    .startSharedOnRandomPort();
            try {
                consumer.accept(sharedServer);
            } catch (Exception e) {
                BaseTest.hide(e);
            } finally {
                sharedServer.dispose();
            }
        }
    }

    @Nested
    class FailFastSuite implements FastTest {

        @Test
        @DisplayName("'alwaysRespondWithStatus' should fail immediately if provided invalid http status")
        void alwaysrespondwithstatus_should_fail_immediately_if_provided_invalid_http_status() {
            for (var invalidHttpStatusCode : List.of(-1, 1, 99, 104, 512, Integer.MAX_VALUE)) {
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .as("should reject http status code [%d]", invalidHttpStatusCode)
                        .isThrownBy(() -> ImpServer.httpTemplate()
                                .alwaysRespond(spec -> spec.withStatus(invalidHttpStatusCode)
                                        .andTextBody("")
                                        .andNoAdditionalHeaders()))
                        .withMessage("Invalid http status code [%d]", invalidHttpStatusCode);
            }
        }

        @Test
        @DisplayName("'alwaysRespondWithStatus' should work for all known status codes")
        void alwaysrespondwithstatus_should_work_for_all_known_status_codes() {
            for (var httpStatus : ImpHttpStatus.values()) {
                assertThatNoException()
                        .as("Should work for http status code [%d]", httpStatus.value())
                        .isThrownBy(
                                () -> ImpServer.httpTemplate().alwaysRespond(spec -> spec.withStatus(httpStatus.value())
                                        .andTextBody("")
                                        .andNoAdditionalHeaders()));
            }
        }

        @Test
        @DisplayName("'andTextBody' should fail immediately if provided null")
        void andtextbody_should_fail_immediately_if_provided_null() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .alwaysRespond(spec ->
                                    spec.withStatus(200).andTextBody(null).andNoAdditionalHeaders()))
                    .withMessage("nulls are not supported - textBody");
        }

        @Test
        @DisplayName("'andXmlBody' should fail immediately if provided null")
        void andxmlbody_should_fail_immediately_if_provided_null() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .alwaysRespond(spec ->
                                    spec.withStatus(200).andXmlBody(null).andNoAdditionalHeaders()))
                    .withMessage("nulls are not supported - xmlBody");
        }

        @Test
        @DisplayName("'andJsonBody' should fail immediately if provided null")
        void andjsonbody_should_fail_immediately_if_provided_null() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .alwaysRespond(spec ->
                                    spec.withStatus(200).andJsonBody(null).andNoAdditionalHeaders()))
                    .withMessage("nulls are not supported - jsonBody");
        }

        @Test
        @DisplayName("'andCustomContentTypeStream' should fail immediately if provided null contentType")
        void andcustomcontenttypestream_should_fail_immediately_if_provided_null_contenttype() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().alwaysRespond(spec -> spec.withStatus(200)
                            .andCustomContentTypeStream(null, () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'andCustomContentTypeStream' should fail immediately if provided null streamSupplier")
        void andcustomcontenttypestream_should_fail_immediately_if_provided_null_streamsupplier() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().alwaysRespond(spec -> spec.withStatus(200)
                            .andCustomContentTypeStream("someType", null)
                            .andNoAdditionalHeaders()))
                    .withMessage("nulls are not supported - dataStreamSupplier");
        }

        @Test
        @DisplayName(
                "'andCustomContentTypeStream' should fail immediately if provided null streamSupplier and contentTypeqq")
        void andcustomcontenttypestream_should_fail_immediately_if_provided_null_streamsupplier_and_contenttypeqq() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().alwaysRespond(spec -> spec.withStatus(200)
                            .andCustomContentTypeStream(null, null)
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'andCustomContentTypeStream' should fail immediately if provided empty contentType")
        void andcustomcontenttypestream_should_fail_immediately_if_provided_empty_contenttype() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().alwaysRespond(spec -> spec.withStatus(200)
                            .andCustomContentTypeStream("", () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'andCustomContentTypeStream' should fail immediately if provided blank contentType")
        void andcustomcontenttypestream_should_fail_immediately_if_provided_blank_contenttype() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().alwaysRespond(spec -> spec.withStatus(200)
                            .andCustomContentTypeStream("   ", () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'matchRequest' when null function then throw exception")
        void matchrequest_when_null_function_then_throw_exception() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(null))
                    .withMessage("nulls are not supported - action");
        }

        @Test
        @SuppressWarnings("DataFlowIssue")
        @DisplayName("'onRequestMatching' when null id then throw exception")
        void onrequestmatching_when_null_id_then_throw_exception() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> {
                        spec.id(null);
                        return null;
                    }))
                    .withMessage("null or blank strings are not supported - id");
        }

        @Test
        @SuppressWarnings("DataFlowIssue")
        @DisplayName("'onRequestMatching' when empty id then throw exception")
        void onrequestmatching_when_empty_id_then_throw_exception() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> {
                        spec.id("");
                        return null;
                    }))
                    .withMessage("null or blank strings are not supported - id");
        }

        @Test
        @SuppressWarnings("DataFlowIssue")
        @DisplayName("'onRequestMatching' when blank id then throw exception")
        void onrequestmatching_when_blank_id_then_throw_exception() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> {
                        spec.id("   ");
                        return null;
                    }))
                    .withMessage("null or blank strings are not supported - id");
        }

        @Test
        @DisplayName("'onRequestMatching' when noop match consumer then ok")
        void onrequestmatching_when_noop_match_consumer_then_ok() {
            assertThatNoException().isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                    .priority(0)
                    .match(ImpMatch::everything)
                    .respondWithStatus(200)
                    .andTextBody("")
                    .andNoAdditionalHeaders()));
        }

        @Test
        @DisplayName("'onRequestMatching' should fail immediately if provided invalid http status")
        void onrequestmatching_should_fail_immediately_if_provided_invalid_http_status() {
            for (var invalidHttpStatusCode : List.of(-1, 1, 99, 104, 512, Integer.MAX_VALUE)) {
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .as("should reject http status code [%d]", invalidHttpStatusCode)
                        .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                                .priority(0)
                                .match(ImpMatch::everything)
                                .respondWithStatus(invalidHttpStatusCode)
                                .andTextBody("")
                                .andNoAdditionalHeaders()))
                        .withMessage("Invalid http status code [%d]", invalidHttpStatusCode);
            }
        }

        @Test
        @DisplayName("'onRequestMatching' should work all known status codes")
        void onrequestmatching_should_work_all_known_status_codes() {
            for (var httpStatus : ImpHttpStatus.values()) {
                assertThatNoException()
                        .as("Should work for http status code [%d]", httpStatus.value())
                        .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                                .priority(0)
                                .match(ImpMatch::everything)
                                .respondWithStatus(httpStatus.value())
                                .andTextBody("")
                                .andNoAdditionalHeaders()));
            }
        }

        @Test
        @DisplayName("'onRequestMatching andTextBody' should reject null")
        void onrequestmatching_andtextbody_should_reject_null() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody(null)
                            .andNoAdditionalHeaders()))
                    .withMessage("nulls are not supported - textBody");
        }

        @Test
        @DisplayName("'onRequestMatching andJsonBody' should reject null")
        void onrequestmatching_andjsonbody_should_reject_null() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andJsonBody(null)
                            .andNoAdditionalHeaders()))
                    .withMessage("nulls are not supported - jsonBody");
        }

        @Test
        @DisplayName("'onRequestMatching andXmlBody' should reject null")
        void onrequestmatching_andxmlbody_should_reject_null() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andXmlBody(null)
                            .andNoAdditionalHeaders()))
                    .withMessage("nulls are not supported - xmlBody");
        }

        @Test
        @DisplayName("'onRequestMatching customContentTypeStream' should reject null content type")
        void onrequestmatching_customcontenttypestream_should_reject_null_content_type() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andCustomContentTypeStream(null, () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'onRequestMatching customContentTypeStream' should reject empty content type")
        void onrequestmatching_customcontenttypestream_should_reject_empty_content_type() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andCustomContentTypeStream("", () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'onRequestMatching customContentTypeStream' should reject blank content type")
        void onrequestmatching_customcontenttypestream_should_reject_blank_content_type() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andCustomContentTypeStream("  ", () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'onRequestMatching customContentTypeStream' should reject null dataStreamSupplier")
        void onrequestmatching_customcontenttypestream_should_reject_null_datastreamsupplier() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andCustomContentTypeStream("contenttpye", null)
                            .andNoAdditionalHeaders()))
                    .withMessage("nulls are not supported - dataStreamSupplier");
        }

        @Test
        @DisplayName("'onRequestMatching andDataStreamBody' should reject null dataStreamBody")
        void onrequestmatching_anddatastreambody_should_reject_null_datastreambody() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andDataStreamBody(null)
                            .andNoAdditionalHeaders()))
                    .withMessage("nulls are not supported - dataStreamSupplier");
        }

        @Test
        @DisplayName(
                "'onRequestMatching customContentTypeStream' should reject null contentType with dataStreamSupplier")
        void onrequestmatching_customcontenttypestream_should_reject_null_contenttype_with_datastreamsupplier() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andCustomContentTypeStream(null, null)
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'onRequestMatching andAdditionalHeaders' should reject null headers")
        void onrequestmatching_andadditionalheaders_should_reject_null_headers() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andAdditionalHeaders(null)))
                    .withMessage("nulls are not supported - headers");
        }

        @Test
        @DisplayName("'onRequestMatching andAdditionalHeaders' should reject nulls entry in map immediately")
        void onrequestmatching_andadditionalheaders_should_reject_nulls_entry_in_map_immediately() {
            var headers = new HashMap<String, List<String>>();
            headers.put(null, null);
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andAdditionalHeaders(headers)))
                    .withMessage("null key are not supported in headers, but found null key in entry [ null=null ]");
        }

        @Test
        @DisplayName("'onRequestMatching andAdditionalHeaders' should reject nulls keys in map immediately")
        void onrequestmatching_andadditionalheaders_should_reject_nulls_keys_in_map_immediately() {
            var headers = new HashMap<String, List<String>>();
            headers.put(null, List.of("something"));
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andAdditionalHeaders(headers)))
                    .withMessage(
                            "null key are not supported in headers, but found null key in entry [ null=[something] ]");
        }

        @Test
        @DisplayName("'onRequestMatching andAdditionalHeaders' should reject nulls values in map immediately")
        void onrequestmatching_andadditionalheaders_should_reject_nulls_values_in_map_immediately() {
            var headers = new HashMap<String, List<String>>();
            headers.put("something", null);
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andAdditionalHeaders(headers)))
                    .withMessage(
                            "null values are not supported in headers, but found null values in entry [ something=null ]");
        }

        @Test
        @DisplayName("'matchRequest method is' should fail immediately if null")
        void matchrequest_method_is_should_fail_immediately_if_null() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.method().is(null))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("'matchRequest method is' should fail immediately if empty")
        void matchrequest_method_is_should_fail_immediately_if_empty() {
            //noinspection MagicConstant
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.method().is(""))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("'matchRequest method is' should fail immediately if blank")
        void matchrequest_method_is_should_fail_immediately_if_blank() {
            //noinspection MagicConstant
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.method().is(""))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("'matchRequest method is' should fail immediately if unknown method")
        void matchrequest_method_is_should_fail_immediately_if_unknown_method() {
            //noinspection MagicConstant
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.method().is("unknownMethod"))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("'matchRequest method anyOf' should fail immediately if unknown method")
        void matchrequest_method_anyof_should_fail_immediately_if_unknown_method() {
            //noinspection MagicConstant
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.method().anyOf("unknownMethod"))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("'matchRequest method anyOf' should fail immediately if null")
        void matchrequest_method_anyof_should_fail_immediately_if_null() {
            String[] expectedMethods = null;
            //noinspection DataFlowIssue,ConstantValue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.method().anyOf(expectedMethods))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("'matchRequest method anyOf' should fail immediately if empty")
        void matchrequest_method_anyof_should_fail_immediately_if_empty() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.method().anyOf())
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("'matchRequest method anyOf' should fail immediately if contains null")
        void matchrequest_method_anyof_should_fail_immediately_if_contains_null() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.method().anyOf("GET", null))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("'matchRequest method anyOf' should fail immediately if contains duplicates")
        void matchrequest_method_anyof_should_fail_immediately_if_contains_duplicates() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.method().anyOf("GET", "GET"))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("should fail immediately when try to negate `everything` matcher")
        void should_fail_immediately_when_try_to_negate_everything_matcher() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.not(match.everything()))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("should fail immediately when try to use `everything` matcher in `and`")
        void should_fail_immediately_when_try_to_use_everything_matcher_in_and() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.and(match.everything()))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("should fail immediately when empty `and` matcher")
        void should_fail_immediately_when_empty_and_matcher() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::and)
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("should fail immediately when empty `or` matcher")
        void should_fail_immediately_when_empty_or_matcher() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::or)
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("should fail immediately when try to use `everything` matcher in `or`")
        void should_fail_immediately_when_try_to_use_everything_matcher_in_or() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.or(match.everything()))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("should fail immediately when try negate nested `everything` matcher")
        void should_fail_immediately_when_try_negate_nested_everything_matcher() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.or(match.and(match.or(match.not(match.everything())))))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("should fail immediately when try negate nested `and` matcher")
        void should_fail_immediately_when_try_negate_nested_and_matcher() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match ->
                                    match.or(match.not(match.and(match.headers().containsKey("key")))))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("should fail immediately when try negate `and` matcher")
        void should_fail_immediately_when_try_negate_and_matcher() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.not(match.and(match.headers().containsKey("key"))))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("should fail immediately when try negate `or` matcher")
        void should_fail_immediately_when_try_negate_or_matcher() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(match -> match.not(match.or(match.headers().containsKey("key"))))
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andNoAdditionalHeaders()))
                    .satisfies(e -> expectSelfie(e.getMessage()).toMatchDisk());
        }

        @Test
        @DisplayName("'startSharedOnPort' should fail immediately if provided negative port")
        void startsharedonport_should_fail_immediately_if_provided_negative_port() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .alwaysRespond(
                                    spec -> spec.withStatus(200).andTextBody("").andNoAdditionalHeaders())
                            .startSharedOnPort(-1))
                    .withMessage("Port value should be greater than 0. Received -1");
        }

        @Test
        @DisplayName("'onPort' should fail immediately if provided negative port")
        void onport_should_fail_immediately_if_provided_negative_port() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .alwaysRespond(
                                    spec -> spec.withStatus(200).andTextBody("").andNoAdditionalHeaders())
                            .onPort(-1))
                    .withMessage("Port value should be greater than 0. Received -1");
        }

        @Test
        @DisplayName("'startSharedOnPort' should fail if provided port 0 and suggest using random port methods")
        void startsharedonport_should_fail_if_provided_port_0_and_suggest_using_random_port_methods() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .alwaysRespond(
                                    spec -> spec.withStatus(200).andTextBody("").andNoAdditionalHeaders())
                            .startSharedOnPort(0))
                    .withMessage(
                            "To use server on random port, use onRandomPort() or startSharedOnRandomPort() methods instead of fixed 0 value");
        }

        @Test
        @DisplayName("'onPort' should fail if provided port 0 and suggest using random port methods")
        void onport_should_fail_if_provided_port_0_and_suggest_using_random_port_methods() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .alwaysRespond(
                                    spec -> spec.withStatus(200).andTextBody("").andNoAdditionalHeaders())
                            .onPort(0))
                    .withMessage(
                            "To use server on random port, use onRandomPort() or startSharedOnRandomPort() methods instead of fixed 0 value");
        }

        @Test
        @DisplayName("'fallbackForNonMatching' should fail immediately if provided null fallbackFn")
        void fallbackfornonmatching_should_fail_immediately_if_provided_null_fallbackfn() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate()
                            .matchRequest(spec -> spec.id("id")
                                    .priority(0)
                                    .match(ImpMatch::everything)
                                    .respondWithStatus(200)
                                    .andTextBody("")
                                    .andNoAdditionalHeaders())
                            .fallbackForNonMatching(null))
                    .withMessage("nulls are not supported - fallbackFn");
        }

        @Test
        @DisplayName("'andBodyBasedOnRequest' should fail immediately if provided null contentType")
        void andbodybasedonrequest_should_fail_immediately_if_provided_null_contenttype() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andBodyBasedOnRequest(null, req -> () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'andBodyBasedOnRequest' should fail immediately if provided empty contentType")
        void andbodybasedonrequest_should_fail_immediately_if_provided_empty_contenttype() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andBodyBasedOnRequest("", req -> () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'andBodyBasedOnRequest' should fail immediately if provided blank contentType")
        void andbodybasedonrequest_should_fail_immediately_if_provided_blank_contenttype() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andBodyBasedOnRequest("   ", req -> () -> new ByteArrayInputStream(new byte[0]))
                            .andNoAdditionalHeaders()))
                    .withMessage("null or blank strings are not supported - contentType");
        }

        @Test
        @DisplayName("'andBodyBasedOnRequest' should fail immediately if provided null bodyFunction")
        void andbodybasedonrequest_should_fail_immediately_if_provided_null_bodyfunction() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andBodyBasedOnRequest("contentType", null)
                            .andNoAdditionalHeaders()))
                    .withMessage("nulls are not supported - bodyFunction");
        }

        @Test
        @DisplayName("'andExactHeaders' should fail immediately if provided null headers")
        void andexactheaders_should_fail_immediately_if_provided_null_headers() {
            //noinspection DataFlowIssue
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andExactHeaders(null)))
                    .withMessage("nulls are not supported - headers");
        }

        @Test
        @DisplayName("'andExactHeaders' should fail immediately if provided headers with null keys")
        void andexactheaders_should_fail_immediately_if_provided_headers_with_null_keys() {
            var headers = new HashMap<String, List<String>>();
            headers.put(null, List.of("value"));
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andExactHeaders(headers)))
                    .withMessage("null key are not supported in headers, but found null key in entry [ null=[value] ]");
        }

        @Test
        @DisplayName("'andExactHeaders' should fail immediately if provided headers with null values")
        void andexactheaders_should_fail_immediately_if_provided_headers_with_null_values() {
            var headers = new HashMap<String, List<String>>();
            headers.put("key", null);
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> ImpServer.httpTemplate().matchRequest(spec -> spec.id("id")
                            .priority(0)
                            .match(ImpMatch::everything)
                            .respondWithStatus(200)
                            .andTextBody("")
                            .andExactHeaders(headers)))
                    .withMessage(
                            "null values are not supported in headers, but found null values in entry [ key=null ]");
        }
    }

    @Language("json")
    private static String jsonFixture() {
        return """
{
  "stringKey": "stringVal",
  "intKey": 123,
  "nullKey": null,
  "trueKey": true,
  "falseKey": false,
  "stringArrayKey": [ "string1", "string2" ]
}
""";
    }
}
