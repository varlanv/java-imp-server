package com.varlanv.imp;

import static org.assertj.core.api.Assertions.assertThat;

import com.varlanv.imp.commontest.FastTest;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
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
}
