package com.varlanv.imp;

import java.math.BigDecimal;
import java.util.Objects;

final class JaywayJsonPathMatch implements JsonPathMatch {

    JsonPathInternal.CompiledPath compiledPath;

    JaywayJsonPathMatch(JsonPathInternal.CompiledPath compiledPath) {
        this.compiledPath = compiledPath;
    }

    @Override
    public ImpCondition isNull() {
        return request -> {
            var ref = request.jsonPathResultRef(compiledPath);
            return ref.isPresent && ref.value == null;
        };
    }

    @Override
    public ImpCondition isPresent() {
        return request -> request.jsonPathResultRef(compiledPath).isPresent;
    }

    @Override
    public ImpCondition isNotPresent() {
        return request -> !request.jsonPathResultRef(compiledPath).isPresent;
    }

    @Override
    public ImpCondition isTrue() {
        return ifType(Boolean.class, val -> val);
    }

    @Override
    public ImpCondition isFalse() {
        return ifType(Boolean.class, val -> !val);
    }

    @Override
    public ImpCondition matches(String pattern) {
        return ifType(String.class, val -> val.matches(pattern));
    }

    @Override
    public ImpCondition stringEquals(String expected) {
        return ifType(String.class, val -> Objects.equals(expected, val));
    }

    @Override
    public ImpCondition numberEquals(long expected) {
        return request -> {
            var ref = request.jsonPathResultRef(compiledPath);
            if (ref.isPresent) {
                if (ref.value instanceof Long) {
                    return (Long) ref.value == expected;
                } else if (ref.value instanceof Integer) {
                    return (Integer) ref.value == expected;
                }
            }
            return false;
        };
    }

    @Override
    public ImpCondition decimalEquals(BigDecimal expected) {
        return request -> {
            var ref = request.jsonPathResultRef(compiledPath);
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
        };
    }

    private <T> ImpCondition ifType(Class<T> type, ImpPredicate<T> predicate) {
        return request -> {
            var ref = request.jsonPathResultRef(compiledPath);
            if (ref.isPresent) {
                if (type.isInstance(ref.value)) {
                    @SuppressWarnings("unchecked")
                    var valCasted = (T) ref.value;
                    return predicate.test(valCasted);
                }
            }
            return false;
        };
    }
}
