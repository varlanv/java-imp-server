package com.varlanv.imp.commontest;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class TestUtils {

    private TestUtils() {}

    private static final AtomicReference<@Nullable Path> projectRoot = new AtomicReference<>();

    @Nullable public static Path projectRoot() {
        return projectRoot.get();
    }

    public static void setProjectRoot(Supplier<Path> fileSupplier) {
        if (projectRoot.get() == null) {
            synchronized (TestUtils.class) {
                projectRoot.set(fileSupplier.get());
            }
        }
    }
}
