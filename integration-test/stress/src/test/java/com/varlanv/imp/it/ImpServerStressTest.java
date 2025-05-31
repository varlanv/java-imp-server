package com.varlanv.imp.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.varlanv.imp.ImpMatch;
import com.varlanv.imp.ImpServer;
import com.varlanv.imp.commontest.SlowTest;
import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ImpServerStressTest implements SlowTest {

    private static final String requestAndResponse = "asd".repeat(10);

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
                    var threadsCount = 10;
                    var tasksPerThreadCount = 100;
                    var tasks = threadsCount * tasksPerThreadCount;
                    var executorService = Executors.newFixedThreadPool(threadsCount);
                    try {
                        var latch = new CountDownLatch(tasks);
                        var allReadyLock = new CompletableFuture<>();
                        var counter = new AtomicInteger();
                        for (var taskIdx = 0; taskIdx < tasks; taskIdx++) {
                            CompletableFuture.runAsync(
                                    () -> {
                                        allReadyLock.join();
                                        sendHttpRequestWithBody(
                                                        impServer.port(),
                                                        requestAndResponse,
                                                        HttpResponse.BodyHandlers.ofString())
                                                .thenAccept(response -> {
                                                    latch.countDown();
                                                    assertThat(response.body()).isEqualTo(requestAndResponse);
                                                    assertThat(response.statusCode())
                                                            .isEqualTo(responseStatus);
                                                    var cnt = counter.incrementAndGet();
                                                    if (cnt % 500 == 0) {
                                                        System.out.printf("Completed %d tasks%n", cnt);
                                                    }
                                                });
                                    },
                                    executorService);
                        }
                        allReadyLock.complete("");
                        latch.await();
                    } finally {
                        executorService.shutdownNow();
                    }
                });
    }
}
