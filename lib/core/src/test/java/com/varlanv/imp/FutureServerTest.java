package com.varlanv.imp;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.sun.net.httpserver.HttpServer;
import com.varlanv.imp.commontest.FastTest;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FutureServerTest implements FastTest {

    @Test
    @DisplayName("should throw exception if failed to acquire specific port within available retries")
    void should_throw_exception_if_failed_to_acquire_specific_port_within_available_retries() throws Exception {
        var port = randomPort();
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        var futureServer = new FutureServer(PortSupplier.fixed(port));

        try {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(futureServer::createServer)
                    .withMessage("Could not acquire port [%d] after [5] retries - port is in use", port);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("should throw exception if failed to acquire 'random' port within available retries")
    void should_throw_exception_if_failed_to_acquire_random_port_within_available_retries() throws Exception {
        var port = randomPort();
        var portSupplier = new PortSupplier() {
            @Override
            public int value() {
                return port;
            }

            @Override
            public boolean isRandom() {
                return true;
            }
        };
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        var futureServer = new FutureServer(portSupplier);

        try {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(futureServer::createServer)
                    .withMessage("Could not acquire random port after [5] retries", port);
        } finally {
            server.stop(0);
        }
    }
}
