package com.varlanv.imp.commontest;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class LazyCloseAwareStream implements Supplier<InputStream> {

    private final BaseTest.ThrowingSupplier<InputStream> streamSupplier;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public LazyCloseAwareStream(BaseTest.ThrowingSupplier<InputStream> streamSupplier) {
        this.streamSupplier = streamSupplier;
    }

    @Override
    public InputStream get() {
        try {
            var inputStream = streamSupplier.get();
            return new InputStream() {

                @Override
                public int read() throws IOException {
                    return inputStream.read();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    return inputStream.read(b, off, len);
                }

                @Override
                public int read(byte[] b) throws IOException {
                    return inputStream.read();
                }

                @Override
                public int readNBytes(byte[] b, int off, int len) throws IOException {
                    return inputStream.readNBytes(b, off, len);
                }

                @Override
                public byte[] readNBytes(int len) throws IOException {
                    return inputStream.readNBytes(len);
                }

                @Override
                public byte[] readAllBytes() throws IOException {
                    return inputStream.readAllBytes();
                }

                @Override
                public void close() throws IOException {
                    isClosed.set(true);
                    inputStream.close();
                }
            };
        } catch (Exception e) {
            return BaseTest.hide(e);
        }
    }

    public boolean isClosed() {
        return isClosed.get();
    }
}
