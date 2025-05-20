package com.varlanv.imp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.varlanv.imp.commontest.FastTest;
import java.io.ByteArrayInputStream;
import java.net.BindException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class ImpServerTest implements FastTest {

    @Test
    @DisplayName("Should be able to start server with random port")
    void should_be_able_to_start_server_with_random_port() {
        ImpServer.template()
                .randomPort()
                .alwaysRespondWithStatus(200)
                .andTextBody("")
                .useServer(impServer -> {});
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
                    var httpClient = HttpClient.newHttpClient();
                    var request =
                            httpRequestBuilderSupplier.apply(impServer.port()).build();
                    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(expected);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers)
                                .containsEntry("Content-Type", List.of(ImpContentType.APPLICATION_JSON.stringValue()));
                        assertThat(headers).containsEntry("Content-Length", List.of("19"));
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
                    var httpClient = HttpClient.newHttpClient();
                    var request =
                            httpRequestBuilderSupplier.apply(impServer.port()).build();
                    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(expected);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers)
                                .containsEntry("Content-Type", List.of(ImpContentType.TEXT_PLAIN.stringValue()));
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
                    var httpClient = HttpClient.newHttpClient();
                    var request =
                            httpRequestBuilderSupplier.apply(impServer.port()).build();
                    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(expected);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers)
                                .containsEntry("Content-Type", List.of(ImpContentType.OCTET_STREAM.stringValue()));
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
                    var httpClient = HttpClient.newHttpClient();
                    var request =
                            httpRequestBuilderSupplier.apply(impServer.port()).build();
                    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(expected);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers)
                                .containsEntry("Content-Type", List.of(ImpContentType.APPLICATION_XML.stringValue()));
                        assertThat(headers).containsEntry("Content-Length", List.of(String.valueOf(expected.length())));
                        assertThat(headers).containsKey("date");
                    });
                });
    }

    @Test
    @DisplayName("should be able to start server at specific port")
    void should_be_able_to_start_server_at_specific_port() {
        var port = InternalUtils.randomPort();
        var someText = "some text";
        ImpServer.template()
                .port(port)
                .alwaysRespondWithStatus(200)
                .andTextBody(someText)
                .useServer(impServer -> {
                    var httpClient = HttpClient.newHttpClient();
                    var request = HttpRequest.newBuilder(
                                    new URI(String.format("http://localhost:%d/", impServer.port())))
                            .build();
                    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.body()).isEqualTo(someText);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().map()).hasSize(3).satisfies(headers -> {
                        assertThat(headers)
                                .containsEntry("Content-Type", List.of(ImpContentType.TEXT_PLAIN.stringValue()));
                        assertThat(headers).containsEntry("Content-Length", List.of(String.valueOf(someText.length())));
                        assertThat(headers).containsKey("date");
                    });
                });
    }

    @Test
    @DisplayName("should throw exception when requested port is already in use")
    void should_throw_exception_when_requested_port_is_already_in_use() throws Exception {
        var port = InternalUtils.randomPort();
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
}
