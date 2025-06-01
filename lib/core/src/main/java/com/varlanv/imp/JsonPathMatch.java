package com.varlanv.imp;

import java.math.BigDecimal;
import org.intellij.lang.annotations.Language;

public interface JsonPathMatch {

    ImpCondition isNull();

    ImpCondition isPresent();

    ImpCondition isNotPresent();

    ImpCondition isTrue();

    ImpCondition isFalse();

    ImpCondition matches(@Language("regexp") String pattern);

    ImpCondition stringEquals(String expected);

    ImpCondition numberEquals(long expected);

    ImpCondition decimalEquals(BigDecimal expected);
}
