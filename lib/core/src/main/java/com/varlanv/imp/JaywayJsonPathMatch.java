package com.varlanv.imp;

import java.math.BigDecimal;
import java.util.Objects;
import org.intellij.lang.annotations.RegExp;

final class JaywayJsonPathMatch implements JsonPathMatch {

    private static final String GROUP = "JsonPath";
    JsonPathInternal.CompiledPath compiledPath;

    JaywayJsonPathMatch(JsonPathInternal.CompiledPath compiledPath) {
        this.compiledPath = compiledPath;
    }

    @Override
    public ImpCondition isNull() {
        return new ImpCondition(
                GROUP,
                request -> {
                    var ref = request.jsonPathResultRef(compiledPath);
                    return ref.isPresent && ref.value == null;
                },
                () -> String.format("%s isNull()", compiledPath.stringPath),
                ImpCondition.Kind.CONDITION);
    }

    @Override
    public ImpCondition isPresent() {
        return new ImpCondition(
                GROUP,
                request -> request.jsonPathResultRef(compiledPath).isPresent,
                () -> String.format("%s isPresent()", compiledPath.stringPath),
                ImpCondition.Kind.CONDITION);
    }

    @Override
    public ImpCondition isNotPresent() {
        return new ImpCondition(
                GROUP,
                request -> !request.jsonPathResultRef(compiledPath).isPresent,
                () -> String.format("%s isNotPresent()", compiledPath.stringPath),
                ImpCondition.Kind.CONDITION);
    }

    @Override
    public ImpCondition isTrue() {
        return new ImpCondition(
                GROUP,
                ifType(Boolean.class, val -> val),
                () -> String.format("%s isTrue()", compiledPath.stringPath),
                ImpCondition.Kind.CONDITION);
    }

    @Override
    public ImpCondition isFalse() {
        return new ImpCondition(
                GROUP,
                ifType(Boolean.class, val -> !val),
                () -> String.format("%s isFalse()", compiledPath.stringPath),
                ImpCondition.Kind.CONDITION);
    }

    @Override
    public ImpCondition matches(@RegExp String pattern) {
        return new ImpCondition(
                GROUP,
                ifType(String.class, val -> val.matches(pattern)),
                () -> String.format("%s matches(\"%s\")", compiledPath.stringPath, pattern),
                ImpCondition.Kind.CONDITION);
    }

    @Override
    public ImpCondition stringEquals(String expected) {
        return new ImpCondition(
                GROUP,
                ifType(String.class, val -> Objects.equals(expected, val)),
                () -> String.format("%s stringEquals(\"%s\")", compiledPath.stringPath, expected),
                ImpCondition.Kind.CONDITION);
    }

    @Override
    public ImpCondition numberEquals(long expected) {
        return new ImpCondition(
                GROUP,
                request -> {
                    var ref = request.jsonPathResultRef(compiledPath);
                    if (ref.isPresent) {
                        if (ref.value instanceof Long) {
                            return (Long) ref.value == expected;
                        } else if (ref.value instanceof Integer) {
                            return (Integer) ref.value == expected;
                        }
                    }
                    return false;
                },
                () -> String.format("%s numberEquals(%s)", compiledPath.stringPath, expected),
                ImpCondition.Kind.CONDITION);
    }

    @Override
    public ImpCondition decimalEquals(BigDecimal expected) {
        return new ImpCondition(
                GROUP,
                request -> {
                    var ref = request.jsonPathResultRef(compiledPath);
                    if (ref.isPresent) {
                        if (ref.value instanceof Double) {
                            return expected.compareTo(BigDecimal.valueOf((Double) ref.value)) == 0;
                        } else if (ref.value instanceof BigDecimal) {
                            return expected.compareTo((BigDecimal) ref.value) == 0;
                        } else if (ref.value instanceof Long) {
                            return expected.compareTo(BigDecimal.valueOf((Long) ref.value)) == 0;
                        } else if (ref.value instanceof Integer) {
                            return expected.compareTo(BigDecimal.valueOf((Integer) ref.value)) == 0;
                        }
                    }
                    return false;
                },
                () -> String.format("%s decimalEquals(%s)", compiledPath.stringPath, expected),
                ImpCondition.Kind.CONDITION);
    }

    private <T> ImpPredicate<ImpRequestView> ifType(Class<T> type, ImpPredicate<T> predicate) {
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
