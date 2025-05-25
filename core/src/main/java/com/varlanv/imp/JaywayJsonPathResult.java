package com.varlanv.imp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

final class JaywayJsonPathResult implements JsonPathResult {

    private final MemoizedSupplier<Optional<?>> valueSupplier;

    JaywayJsonPathResult(MemoizedSupplier<DocumentContext> jsonPath, String path) {
        valueSupplier = MemoizedSupplier.of(() -> {
            var documentContext = jsonPath.get();
            try {
                return Optional.ofNullable(documentContext.read(path));
            } catch (PathNotFoundException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public boolean isNull() {
        var val = valueSupplier.get();
        return val.isPresent();
    }

    @Override
    public boolean isPresent() {
        return valueSupplier.get().isPresent();
    }

    @Override
    public boolean isNotPresent() {
        return valueSupplier.get().isEmpty();
    }

    @Override
    public boolean isTrue() {
        return ifType(Boolean.class, val -> val);
    }

    @Override
    public boolean isFalse() {
        return ifType(Boolean.class, val -> !val);
    }

    @Override
    public boolean matches(String pattern) {
        return ifType(String.class, val -> val.matches(pattern));
    }

    @Override
    public boolean stringEquals(String expected) {
        return ifType(String.class, val -> Objects.equals(expected, val));
    }

    @Override
    public boolean numberEquals(long expected) {
        var o = valueSupplier.get();
        if (o.isPresent()) {
            var val = o.get();
            if (val instanceof Long) {
                return (Long) val == expected;
            } else if (val instanceof Integer) {
                return (Integer) val == expected;
            }
        }
        return false;
    }

    @Override
    public boolean decimalEquals(BigDecimal expected) {
        var o = valueSupplier.get();
        if (o.isPresent()) {
            var val = o.get();
            if (val instanceof Double) {
                return Objects.equals(expected, BigDecimal.valueOf((Double) val));
            } else if (val instanceof BigDecimal) {
                return Objects.equals(expected, val);
            }
        }
        return false;
    }

    private <T> boolean ifType(Class<T> type, ImpPredicate<T> predicate) {
        var o = valueSupplier.get();
        if (o.isPresent()) {
            var val = o.get();
            if (type.isInstance(val)) {
                @SuppressWarnings("unchecked")
                var valCasted = (T) val;
                return predicate.test(valCasted);
            }
        }
        return false;
    }
}
