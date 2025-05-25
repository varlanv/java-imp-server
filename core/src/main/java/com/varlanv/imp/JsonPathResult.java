package com.varlanv.imp;

import java.math.BigDecimal;
import org.intellij.lang.annotations.Language;

public interface JsonPathResult {

    boolean isNull();

    boolean isPresent();

    boolean isNotPresent();

    boolean isTrue();

    boolean isFalse();

    boolean matches(@Language("regexp") String pattern);

    boolean stringEquals(String expected);

    boolean numberEquals(long expected);

    boolean decimalEquals(BigDecimal expected);
}
