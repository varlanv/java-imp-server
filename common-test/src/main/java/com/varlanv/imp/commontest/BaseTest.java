package com.varlanv.imp.commontest;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

@Execution(ExecutionMode.CONCURRENT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public interface BaseTest {

    HttpClient CLIENT = HttpClient.newHttpClient();
    String SLOW_TEST_TAG = "slow-test";
    String FAST_TEST_TAG = "fast-test";

    default Path newTempDir() {
        return supplyQuiet(() -> {
            var dir = Files.createTempDirectory("testsyncjunit-");
            dir.toFile().deleteOnExit();
            return dir;
        });
    }

    default Path newTempFile() {
        return supplyQuiet(() -> {
            var file = Files.createTempFile("testsyncjunit-", "");
            file.toFile().deleteOnExit();
            return file;
        });
    }

    default void useTempDir(ThrowingConsumer<Path> action) {
        runQuiet(() -> {
            var dir = newTempDir();
            try {
                action.accept(dir);
            } finally {
                try (var paths = Files.walk(dir)) {
                    paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
        });
    }

    default <T> T useTempFile(ThrowingFunction<Path, T> action) {
        return supplyQuiet(() -> {
            var file = newTempFile();
            try {
                return action.apply(file);
            } finally {
                Files.deleteIfExists(file);
            }
        });
    }

    default void consumeTempFile(ThrowingConsumer<Path> action) {
        runQuiet(() -> {
            var file = newTempFile();
            try {
                action.accept(file);
            } finally {
                Files.deleteIfExists(file);
            }
        });
    }

    default void runAndDeleteFile(@NonNull Path file, ThrowingRunnable runnable) {
        runQuiet(() -> {
            try {
                runnable.run();
            } finally {
                if (Files.isRegularFile(file)) {
                    Files.deleteIfExists(file);
                } else {
                    try (var paths = Files.walk(file)) {
                        paths.sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                    }
                }
            }
        });
    }

    interface ThrowingRunnable {
        void run() throws Exception;

        default Runnable toUnchecked() {
            return () -> {
                try {
                    run();
                } catch (Exception e) {
                    hide(e);
                }
            };
        }
    }

    interface ThrowingSupplier<T> {
        T get() throws Exception;

        default Supplier<T> toUnchecked() {
            return () -> {
                try {
                    return get();
                } catch (Exception e) {
                    return hide(e);
                }
            };
        }
    }

    interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;

        default Consumer<T> toUnchecked() {
            return t -> {
                try {
                    accept(t);
                } catch (Exception e) {
                    hide(e);
                }
            };
        }
    }

    interface ThrowingPredicate<T> {
        boolean test(T t) throws Exception;

        default Predicate<T> toUnnchecked() {
            return t -> {
                try {
                    return test(t);
                } catch (Exception e) {
                    return hide(e);
                }
            };
        }
    }

    interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;

        default Function<T, R> toUnchecked() {
            return t -> {
                try {
                    return apply(t);
                } catch (Exception e) {
                    return hide(e);
                }
            };
        }
    }

    class HttpRequestBuilderSource implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            Stream<Function<Integer, HttpRequest.Builder>> result = Stream.of(
                            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "TRACE", "CONNECT", "HEAD")
                    .map(method -> port -> HttpRequest.newBuilder()
                            .uri(supplyQuiet(() -> new URI(String.format("http://localhost:%d/", port)))));
            return result.map(Arguments::of);
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Throwable, R> R hide(Throwable t) throws T {
        throw (T) t;
    }

    static <T> T supplyQuiet(ThrowingSupplier<T> supplier) {
        return supplier.toUnchecked().get();
    }

    static void runQuiet(ThrowingRunnable runnable) {
        runnable.toUnchecked().run();
    }

    default <T> CompletableFuture<HttpResponse<T>> sendHttpRequest(
            HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        try {
            return CLIENT.sendAsync(request, responseBodyHandler);
        } catch (Exception e) {
            return hide(e);
        }
    }

    default <T> CompletableFuture<HttpResponse<T>> sendHttpRequest(
            int port, HttpResponse.BodyHandler<T> responseBodyHandler) {
        try {
            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", port)))
                    .build();
            return sendHttpRequest(request, responseBodyHandler);
        } catch (Exception e) {
            return hide(e);
        }
    }

    default String responseToString(HttpResponse<?> response) {
        Object body = response.body();
        if (body.getClass() == byte[].class) {
            body = new String((byte[]) body, StandardCharsets.UTF_8);
        } else {
            body = String.valueOf(body);
        }
        var originalHeaders = response.headers().map();
        var modifiedHeaders = new LinkedHashMap<String, List<String>>(originalHeaders.size());
        originalHeaders.forEach((key, values) -> {
            var keyLower = key.toLowerCase();
            if ("date".equals(keyLower)) {
                modifiedHeaders.put(keyLower, List.of("<present>"));
            } else {
                modifiedHeaders.put(keyLower, values);
            }
        });

        return "Response status code: " + response.statusCode() + '\n' + "Response headers: "
                + modifiedHeaders + '\n' + "Response body: "
                + body + '\n';
    }

    default <T> CompletableFuture<HttpResponse<T>> sendHttpRequestWithBody(
            int port, String body, HttpResponse.BodyHandler<T> responseBodyHandler) {
        try {
            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", port)))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            return sendHttpRequest(request, responseBodyHandler);
        } catch (Exception e) {
            return hide(e);
        }
    }

    default <T> CompletableFuture<HttpResponse<T>>[] sendManyHttpRequests(
            int count, int port, HttpResponse.BodyHandler<T> responseBodyHandler) {
        try {
            var futures = new CompletableFuture<?>[count];
            var request = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", port)))
                    .build();
            IntStream.range(0, count).forEach(i -> futures[i] = sendHttpRequest(request, responseBodyHandler));
            @SuppressWarnings("unchecked")
            var result = (CompletableFuture<HttpResponse<T>>[]) futures;
            return result;
        } catch (Exception e) {
            return hide(e);
        }
    }

    default <T> CompletableFuture<HttpResponse<T>> sendHttpRequestWithHeaders(
            int port, Map<String, List<String>> headers, HttpResponse.BodyHandler<T> responseBodyHandler) {
        try {
            var requestBuilder = HttpRequest.newBuilder(new URI(String.format("http://localhost:%d/", port)));
            headers.forEach((key, valuesList) -> valuesList.forEach(value -> requestBuilder.header(key, value)));
            return sendHttpRequest(requestBuilder.build(), responseBodyHandler);
        } catch (Exception e) {
            return hide(e);
        }
    }

    default int randomPort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return hide(e);
        }
    }
}
