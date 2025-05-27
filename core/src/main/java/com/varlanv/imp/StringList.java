package com.varlanv.imp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class StringList {

    private final List<Object> parts;

    private StringList(List<Object> parts) {
        this.parts = parts;
    }

    StringList() {
        this(new ArrayList<>());
    }

    StringList addSupplier(Supplier<String> supplier) {
        parts.add(supplier);
        return this;
    }

    StringList addString(String string) {
        parts.add(string);
        return this;
    }

    StringList merge(StringList other) {
        var newList = new ArrayList<>(parts.size() + other.parts.size());
        newList.addAll(parts);
        newList.addAll(other.parts);
        return new StringList(newList);
    }

    StringList addAll(StringList other) {
        parts.addAll(other.parts);
        return this;
    }

    StringBuilder joinToBuilder() {
        var sb = new StringBuilder();
        for (var part : parts) {
            if (part instanceof Supplier) {
                @SuppressWarnings("unchecked")
                var supplier = (Supplier<String>) part;
                sb.append(supplier.get());
            } else {
                sb.append(part);
            }
        }
        return sb;
    }
}
