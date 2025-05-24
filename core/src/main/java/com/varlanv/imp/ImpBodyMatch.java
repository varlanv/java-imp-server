package com.varlanv.imp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ImpBodyMatch {

    private final MemoizedSupplier<byte[]> bodySupplier;
    private final MemoizedSupplier<String> stringBodySupplier;

    ImpBodyMatch(ImpSupplier<InputStream> body) {
        this.bodySupplier = MemoizedSupplier.of(() -> {
            try (var b = body.get()) {
                return b.readAllBytes();
            }
        });
        this.stringBodySupplier = MemoizedSupplier.of(() -> new String(bodySupplier.get(), StandardCharsets.UTF_8));
    }

    public boolean bodyContains(String substring) {
        Preconditions.nonNull(substring, "substring");
        return stringBodySupplier.get().contains(substring);
    }

    public boolean bodyContainsIgnoreCase(String substring) {
        Preconditions.nonNull(substring, "substring");
        return stringBodySupplier.get().toLowerCase().contains(substring.toLowerCase());
    }
}
