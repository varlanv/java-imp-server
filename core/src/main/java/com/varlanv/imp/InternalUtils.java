package com.varlanv.imp;

import java.io.IOException;
import java.net.ServerSocket;

interface InternalUtils {

    @SuppressWarnings("unchecked")
    static <T extends Throwable, R> R hide(Throwable t) throws T {
        throw (T) t;
    }

    static int randomPort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return hide(e);
        }
    }
}
