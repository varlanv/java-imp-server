package com.varlanv.imp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.intellij.lang.annotations.Language;

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

    public boolean bodyMatches(@Language("regexp") String pattern) {
        Preconditions.nonNull(pattern, "pattern");
        return stringBodySupplier.get().matches(pattern);
    }

    public boolean bodyContainsIgnoreCase(String substring) {
        Preconditions.nonNull(substring, "substring");
        return stringBodySupplier.get().toLowerCase().contains(substring.toLowerCase());
    }

    public boolean testBodyString(ImpPredicate<String> predicate) {
        Preconditions.nonNull(predicate, "predicate");
        return predicate.test(stringBodySupplier.get());
    }
}
