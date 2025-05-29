package com.varlanv.imp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class StringList {

    private final List<Supplier<String>> parts;

    private StringList(List<Supplier<String>> parts) {
        this.parts = parts;
    }

    StringList() {
        this(new ArrayList<>());
    }

    void addSupplier(Supplier<String> supplier) {
        parts.add(supplier);
    }

    void addAll(StringList other) {
        parts.addAll(other.parts);
    }

    StringBuilder joinToBuilder() {
        var sb = new StringBuilder();
        for (var part : parts) {
            sb.append(part.get());
        }
        return sb;
    }
}
