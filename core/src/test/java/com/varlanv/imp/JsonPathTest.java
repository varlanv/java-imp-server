package com.varlanv.imp;

import static org.assertj.core.api.Assertions.*;

import com.varlanv.imp.commontest.FastTest;
import java.math.BigDecimal;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonPathTest implements FastTest {

    @Language("json")
    private static final String json =
            """
        {
            "stringKey": "stringValue",
            "trueStringKey": "true",
            "falseStringKey": "false",
            "doubleKey": 123.456,
            "decimalKey": 123.45623123123456789,
            "intKey": 1234567,
            "longKey": 1234444444444444444,
            "trueKey": true,
            "falseKey": false,
            "nullKey": null
        }
        """;

    @Test
    @DisplayName("`stringEquals` returns true when json matches")
    void stringequals_returns_true_when_json_matches() {
        assertThat(JsonPath.forJson(json).apply("$.stringKey").stringEquals("stringValue"))
                .isTrue();
    }

    @Test
    @DisplayName("`stringEquals` returns false when wrong path")
    void stringequals_returns_false_when_wrong_path() {
        assertThat(JsonPath.forJson(json).apply("$.stringKeyy").stringEquals("stringValue"))
                .isFalse();
    }

    @Test
    @DisplayName("`stringEquals` returns false when wrong value")
    void stringequals_returns_false_when_wrong_value() {
        assertThat(JsonPath.forJson(json).apply("$.stringKey").stringEquals("stringValue123"))
                .isFalse();
    }

    @Test
    @DisplayName("`isPresent` returns true when finds")
    void ispresent_returns_true_when_finds() {
        assertThat(JsonPath.forJson(json).apply("$.stringKey").isPresent()).isTrue();
    }

    @Test
    @DisplayName("`isPresent` returns false when not finds")
    void ispresent_returns_false_when_not_finds() {
        assertThat(JsonPath.forJson(json).apply("$.stringKeyy").isPresent()).isFalse();
    }

    @Test
    @DisplayName("`isNotPresent` returns true when not finds")
    void isnotpresent_returns_true_when_not_finds() {
        assertThat(JsonPath.forJson(json).apply("$.stringKeyy").isNotPresent()).isTrue();
    }

    @Test
    @DisplayName("`isNotPresent` returns false when finds")
    void isnotpresent_returns_false_when_finds() {
        assertThat(JsonPath.forJson(json).apply("$.stringKey").isNotPresent()).isFalse();
    }

    @Test
    @DisplayName("`isTrue` returns true when true val")
    void istrue_returns_true_when_true_val() {
        assertThat(JsonPath.forJson(json).apply("$.trueKey").isTrue()).isTrue();
    }

    @Test
    @DisplayName("`isTrue` returns false when false val")
    void istrue_returns_false_when_false_val() {
        assertThat(JsonPath.forJson(json).apply("$.falseKey").isTrue()).isFalse();
    }

    @Test
    @DisplayName("`isTrue` returns false when null val")
    void istrue_returns_false_when_null_val() {
        assertThat(JsonPath.forJson(json).apply("$.nullKey").isTrue()).isFalse();
    }

    @Test
    @DisplayName("`isFalse` returns true when false val")
    void isfalse_returns_true_when_false_val() {
        assertThat(JsonPath.forJson(json).apply("$.falseKey").isFalse()).isTrue();
    }

    @Test
    @DisplayName("`isFalse` returns false when true val")
    void isfalse_returns_false_when_true_val() {
        assertThat(JsonPath.forJson(json).apply("$.trueKey").isFalse()).isFalse();
    }

    @Test
    @DisplayName("`isFalse` returns false when null val")
    void isfalse_returns_false_when_null_val() {
        assertThat(JsonPath.forJson(json).apply("$.nullKey").isFalse()).isFalse();
    }

    @Test
    @DisplayName("`isNull` returns true when null val")
    void isnull_returns_true_when_null_val() {
        assertThat(JsonPath.forJson(json).apply("$.nullKey").isNull()).isTrue();
    }

    @Test
    @DisplayName("`isNull` returns false when val not present")
    void isnull_returns_false_when_val_not_present() {
        assertThat(JsonPath.forJson(json).apply("$.notPresentVal").isNull()).isFalse();
    }

    @Test
    @DisplayName("`isNull` returns false when true val")
    void isnull_returns_false_when_true_val() {
        assertThat(JsonPath.forJson(json).apply("$.trueKey").isNull()).isFalse();
    }

    @Test
    @DisplayName("`isNull` returns false when false val")
    void isnull_returns_false_when_false_val() {
        assertThat(JsonPath.forJson(json).apply("$.falseKey").isNull()).isFalse();
    }

    @Test
    @DisplayName("`isNull` returns false when string val")
    void isnull_returns_false_when_string_val() {
        assertThat(JsonPath.forJson(json).apply("$.stringKey").isNull()).isFalse();
    }

    @Test
    @DisplayName("`matches` returns true when string val matches")
    void matches_returns_true_when_string_val_matches() {
        assertThat(JsonPath.forJson(json).apply("$.stringKey").matches("str.*")).isTrue();
    }

    @Test
    @DisplayName("`matches` returns false when string val not matches")
    void matches_returns_false_when_string_val_not_matches() {
        assertThat(JsonPath.forJson(json).apply("$.stringKey").matches("strr.*"))
                .isFalse();
    }

    @Test
    @DisplayName("`matches` returns false when null val")
    void matches_returns_false_when_null_val() {
        assertThat(JsonPath.forJson(json).apply("$.nullKey").matches("strr.*")).isFalse();
        assertThat(JsonPath.forJson(json).apply("$.nullKey").matches("null")).isFalse();
    }

    @Test
    @DisplayName("`matches` returns false when true val")
    void matches_returns_false_when_true_val() {
        assertThat(JsonPath.forJson(json).apply("$.trueKey").matches("true")).isFalse();
    }

    @Test
    @DisplayName("`numberEquals` returns true when number val equals")
    void numberequals_returns_true_when_number_val_equals() {
        assertThat(JsonPath.forJson(json).apply("$.intKey").numberEquals(1234567))
                .isTrue();
        assertThat(JsonPath.forJson(json).apply("$.longKey").numberEquals(1234444444444444444L))
                .isTrue();
    }

    @Test
    @DisplayName("`numberEquals` returns false when double val")
    void numberequals_returns_false_when_double_val() {
        assertThat(JsonPath.forJson(json).apply("$.doubleKey").numberEquals(123456))
                .isFalse();
    }

    @Test
    @DisplayName("`numberEquals` returns false when val not presentt")
    void numberequals_returns_false_when_val_not_presentt() {
        assertThat(JsonPath.forJson(json).apply("$.notPresentVal").numberEquals(123456))
                .isFalse();
    }

    @Test
    @DisplayName("`numberEquals` returns false when null val")
    void numberequals_returns_false_when_null_val() {
        assertThat(JsonPath.forJson(json).apply("$.nullKey").numberEquals(123456))
                .isFalse();
    }

    @Test
    @DisplayName("`decimalEquals` returns true when double val")
    void decimalequals_returns_true_when_double_val() {
        assertThat(JsonPath.forJson(json).apply("$.doubleKey").decimalEquals(new BigDecimal("123.456")))
                .isTrue();
    }

    @Test
    @DisplayName("`decimalEquals` returns true when decimal val")
    void decimalequals_returns_true_when_decimal_val() {
        assertThat(JsonPath.forJson(json).apply("$.decimalKey").decimalEquals(new BigDecimal("123.45623123123456789")))
                .isTrue();
    }

    @Test
    @DisplayName("`decimalEquals` returns false when decimal val not equal")
    void decimalequals_returns_false_when_decimal_val_not_equal() {
        assertThat(JsonPath.forJson(json).apply("$.doubleKey").decimalEquals(new BigDecimal("123.45666")))
                .isFalse();
    }

    @Test
    @DisplayName("`decimalEquals` returns false when null val")
    void decimalequals_returns_false_when_null_val() {
        assertThat(JsonPath.forJson(json).apply("$.nullKey").decimalEquals(new BigDecimal("123.45666")))
                .isFalse();
    }

    @Test
    @DisplayName("`decimalEquals` returns false when val not present")
    void decimalequals_returns_false_when_val_not_present() {
        assertThat(JsonPath.forJson(json).apply("$.notPresentVal").decimalEquals(new BigDecimal("123.45666")))
                .isFalse();
    }

    @Test
    @DisplayName("`1234567` returns true when number val equals")
    void returns_true_when_number_val_equals() {
        assertThat(JsonPath.forJson(json).apply("$.intKey").decimalEquals(BigDecimal.valueOf(1234567)))
                .isTrue();
        assertThat(JsonPath.forJson(json).apply("$.longKey").decimalEquals(BigDecimal.valueOf(1234444444444444444L)))
                .isTrue();
    }
}
