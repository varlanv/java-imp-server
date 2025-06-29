package com.varlanv.imp;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.Unmodifiable;

interface InternalUtils {

    @SuppressWarnings({"all", "unchecked"})
    static <T extends Throwable, R> R hide(Throwable t) throws T {
        throw (T) t;
    }

    static Supplier<String> emptyStringSupplier() {
        return () -> "";
    }

    static int randomPort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return hide(e);
        }
    }

    @Unmodifiable
    static <E> List<E> addToNewListFinal(List<E> list, E element) {
        var newList = new ArrayList<E>(list.size() + 1);
        newList.addAll(list);
        newList.add(element);
        return Collections.unmodifiableList(newList);
    }

    static byte[] readAllBytesFromSupplier(ImpSupplier<InputStream> supplier) {
        try (var stream = supplier.get()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            return hide(e);
        }
    }
}
