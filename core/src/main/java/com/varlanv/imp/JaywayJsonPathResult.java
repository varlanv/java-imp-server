package com.varlanv.imp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;
import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class JaywayJsonPathResult implements JsonPathResult {

    private final MemoizedSupplier<Ref> valueSupplier;

    JaywayJsonPathResult(MemoizedSupplier<DocumentContext> jsonPath, String path) {
        valueSupplier = MemoizedSupplier.of(() -> {
            var documentContext = jsonPath.get();
            try {
                return new Ref(documentContext.read(path), true);
            } catch (PathNotFoundException e) {
                return new Ref(null, false);
            }
        });
    }

    @Override
    public boolean isNull() {
        var ref = valueSupplier.get();
        return ref.isPresent && ref.value == null;
    }

    @Override
    public boolean isPresent() {
        return valueSupplier.get().isPresent;
    }

    @Override
    public boolean isNotPresent() {
        return !isPresent();
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
        var ref = valueSupplier.get();
        if (ref.isPresent) {
            if (ref.value instanceof Long) {
                return (Long) ref.value == expected;
            } else if (ref.value instanceof Integer) {
                return (Integer) ref.value == expected;
            }
        }
        return false;
    }

    @Override
    public boolean decimalEquals(BigDecimal expected) {
        var ref = valueSupplier.get();
        if (ref.isPresent) {
            if (ref.value instanceof Double) {
                return Objects.equals(expected, BigDecimal.valueOf((Double) ref.value));
            } else if (ref.value instanceof BigDecimal) {
                return Objects.equals(expected, ref.value);
            } else if (ref.value instanceof Long) {
                return Objects.equals(expected, BigDecimal.valueOf((Long) ref.value));
            } else if (ref.value instanceof Integer) {
                return Objects.equals(expected, BigDecimal.valueOf((Integer) ref.value));
            }
        }
        return false;
    }

    private <T> boolean ifType(Class<T> type, ImpPredicate<T> predicate) {
        var ref = valueSupplier.get();
        if (ref.isPresent) {
            if (type.isInstance(ref.value)) {
                @SuppressWarnings("unchecked")
                var valCasted = (T) ref.value;
                return predicate.test(valCasted);
            }
        }
        return false;
    }

    private static final class Ref {

        @Nullable private final Object value;

        private final boolean isPresent;

        private Ref(@Nullable Object value, boolean isPresent) {
            this.value = value;
            this.isPresent = isPresent;
        }
    }
}
