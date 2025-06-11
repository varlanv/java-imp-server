package com.varlanv.imp.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.varlanv.imp.ImpMatch;
import com.varlanv.imp.ImpServer;
import com.varlanv.imp.commontest.SlowTest;
import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * This test doesn't introduce a lot of stress on the server currently,
 * because the current implementation is designed for fast startup time, instead
 * of high runtime performance.
 */
class ImpServerStressTest implements SlowTest {

    private static final String requestAndResponse = "asd".repeat(100);

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    @DisplayName("stress")
    void stress() {
        var responseStatus = 200;
        ImpServer.httpTemplate()
                .matchRequest(spec -> spec.id("matcherId")
                        .priority(1)
                        .match(ImpMatch::everything)
                        .respondWithStatus(responseStatus)
                        .andBodyBasedOnRequest(
                                "text/plain",
                                r -> () ->
                                        new ByteArrayInputStream(requestAndResponse.getBytes(StandardCharsets.UTF_8)))
                        .andNoAdditionalHeaders())
                .rejectNonMatching()
                .onRandomPort()
                .useServer(impServer -> {
                    var threadsCount = 5;
                    var tasksPerThreadCount = 20;
                    var tasks = threadsCount * tasksPerThreadCount;
                    var executorService = Executors.newFixedThreadPool(threadsCount);
                    try {
                        var allReadyLock = new CompletableFuture<>();
                        var successCount = new AtomicInteger();
                        var errorsQueue = new ConcurrentLinkedQueue<Throwable>();
                        var futures = new CompletableFuture<?>[tasks];
                        for (var taskIdx = 0; taskIdx < tasks; taskIdx++) {
                            futures[taskIdx] = CompletableFuture.runAsync(allReadyLock::join, executorService)
                                    .thenCompose(ignore -> sendHttpRequestWithBody(
                                                    impServer.port(),
                                                    requestAndResponse,
                                                    HttpResponse.BodyHandlers.ofString())
                                            .thenAccept(response -> {
                                                assertThat(response.body()).isEqualTo(requestAndResponse);
                                                assertThat(response.statusCode())
                                                        .isEqualTo(responseStatus);
                                                var cnt = successCount.incrementAndGet();
                                                if (cnt % 100 == 0) {
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
                });
    }
}
