package com.varlanv.imp.it;

import static com.github.dreamhead.moco.Runner.running;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dreamhead.moco.Moco;
import com.varlanv.imp.ImpServer;
import com.varlanv.imp.commontest.SlowTest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Disabled
class ImpServerPerformanceComparisonTest implements SlowTest {

    private static final String requestAndResponse = "asd".repeat(1000);
    private static final int responseStatus = 200;
    private static final int threadsCount = 5;
    private static final int tasksPerThreadCount = 20;
    private static final int tasks = threadsCount * tasksPerThreadCount;

    @Test
    @DisplayName("imp simple")
    void imp_simple() {
        ImpServer.httpTemplate()
                .alwaysRespond(spec -> spec.withStatus(responseStatus)
                        .andTextBody(requestAndResponse)
                        .andNoAdditionalHeaders())
                .onRandomPort()
                .useServer(impServer -> {
                    sendHttpRequestWithBody(impServer.port(), requestAndResponse, HttpResponse.BodyHandlers.ofString())
                            .join();
                });
    }

    @Test
    @DisplayName("imp sequential")
    void imp_sequential() {
        ImpServer.httpTemplate()
                .alwaysRespond(spec -> spec.withStatus(responseStatus)
                        .andTextBody(requestAndResponse)
                        .andNoAdditionalHeaders())
                .onRandomPort()
                .useServer(impServer -> testSequential("imp", impServer.port()));
    }

    @Test
    @DisplayName("imp parallel")
    void imp_parallel() {
        ImpServer.httpTemplate()
                .alwaysRespond(spec -> spec.withStatus(responseStatus)
                        .andTextBody(requestAndResponse)
                        .andNoAdditionalHeaders())
                .onRandomPort()
                .useServer(impServer -> testSequential("imp", impServer.port()));
    }

    @Test
    @DisplayName("moco parallel")
    void moco_parallel() throws Exception {
        var httpServer = Moco.httpServer();
        httpServer.response(Moco.with(requestAndResponse), Moco.status(responseStatus));
        running(httpServer, () -> testConcurrent("moco", httpServer.port()));
    }

    @Test
    @DisplayName("moco sequential")
    void moco_sequential() throws Exception {
        var httpServer = Moco.httpServer();
        httpServer.response(Moco.with(requestAndResponse), Moco.status(responseStatus));
        running(httpServer, () -> testSequential("moco", httpServer.port()));
    }

    @Test
    @DisplayName("imp startup")
    void imp_startup() throws Exception {
        testStartup("imp", () -> ImpServer.httpTemplate()
                .alwaysRespond(spec ->
                        spec.withStatus(200).andTextBody(requestAndResponse).andNoAdditionalHeaders())
                .onRandomPort()
                .useServer(ImpServer::port));
    }

    @Test
    @DisplayName("moco startup")
    void moco_startup() throws Exception {
        testStartup("moco", () -> {
            var httpServer = Moco.httpServer();
            httpServer.response(Moco.with(requestAndResponse), Moco.status(responseStatus));
            running(httpServer, httpServer::port);
        });
    }

    private void testStartup(String subject, ThrowingRunnable action) throws Exception {
        action.run();
        var timeBefore = System.nanoTime();
        action.run();
        var timeAfter = System.nanoTime();
        System.err.printf("%s - starts in: %s%n", subject, Duration.ofNanos(timeAfter - timeBefore));
    }

    private void testConcurrent(String subject, int port) {
        var executorService = Executors.newFixedThreadPool(threadsCount);
        var timeBefore = System.nanoTime();
        try {
            var allReadyLock = new CompletableFuture<>();
            var successCount = new AtomicInteger();
            var errorsQueue = new ConcurrentLinkedQueue<Throwable>();
            var futures = new CompletableFuture<?>[tasks];
            for (var taskIdx = 0; taskIdx < tasks; taskIdx++) {
                futures[taskIdx] = CompletableFuture.runAsync(allReadyLock::join, executorService)
                        .thenCompose(ignore -> sendHttpRequestWithBody(
                                        port, requestAndResponse, HttpResponse.BodyHandlers.ofString())
                                .thenAccept(response -> {
                                    assertThat(response.body()).isEqualTo(requestAndResponse);
                                    assertThat(response.statusCode()).isEqualTo(responseStatus);
                                    var cnt = successCount.incrementAndGet();
                                    if (cnt % 50 == 0) {
                                        System.out.printf("Completed %d tasks%n", cnt);
                                    }
                                })
                                .exceptionally(ex -> {
                                    errorsQueue.add(ex);
                                    return null;
                                }));
            }
            allReadyLock.complete("");
            CompletableFuture.allOf(futures).join();
            if (!errorsQueue.isEmpty()) {
                throw new AssertionError("There were errors during stress test", errorsQueue.peek());
            }
            assertThat(successCount.get()).isEqualTo(tasks);
        } finally {
            executorService.shutdownNow();
        }
        System.err.printf(
                "%s - all tasks completed in: %s%n", subject, Duration.ofNanos(System.nanoTime() - timeBefore));
    }

    private void testSequential(String subject, int port) {
        var timeBefore = System.nanoTime();
        for (var taskIdx = 0; taskIdx < tasks; taskIdx++) {
            sendHttpRequestWithBody(port, requestAndResponse, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        assertThat(response.body()).isEqualTo(requestAndResponse);
                        assertThat(response.statusCode()).isEqualTo(responseStatus);
                    })
                    .join();
        }
        System.err.printf(
                "%s - all tasks completed in: %s%n", subject, Duration.ofNanos(System.nanoTime() - timeBefore));
    }
}
