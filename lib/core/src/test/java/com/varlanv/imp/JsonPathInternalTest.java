package com.varlanv.imp;

import static org.assertj.core.api.Assertions.*;

import com.varlanv.imp.commontest.BaseTest;
import com.varlanv.imp.commontest.FastTest;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonPathInternalTest implements FastTest {

    @Language("json")
    private static final String defaultJson =
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
        assertThat(JsonPathInternal.forJsonPath("$.stringKey")
                        .stringEquals("stringValue")
                        .test(defaultRequestViewJson()))
                .isTrue();
    }

    @Test
    @DisplayName("`stringEquals` returns false when wrong path")
    void stringequals_returns_false_when_wrong_path() {
        assertThat(JsonPathInternal.forJsonPath("$.stringKeyy")
                        .stringEquals("stringValue")
                        .test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`stringEquals` returns false when wrong value")
    void stringequals_returns_false_when_wrong_value() {
        assertThat(JsonPathInternal.forJsonPath("$.stringKey")
                        .stringEquals("stringValue123")
                        .test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`isPresent` returns true when finds")
    void ispresent_returns_true_when_finds() {
        assertThat(JsonPathInternal.forJsonPath("$.stringKey").isPresent().test(defaultRequestViewJson()))
                .isTrue();
    }

    @Test
    @DisplayName("`isPresent` returns false when not finds")
    void ispresent_returns_false_when_not_finds() {
        assertThat(JsonPathInternal.forJsonPath("$.stringKeyy").isPresent().test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`isNotPresent` returns true when not finds")
    void isnotpresent_returns_true_when_not_finds() {
        assertThat(JsonPathInternal.forJsonPath("$.stringKeyy").isNotPresent().test(defaultRequestViewJson()))
                .isTrue();
    }

    @Test
    @DisplayName("`isNotPresent` returns false when finds")
    void isnotpresent_returns_false_when_finds() {
        assertThat(JsonPathInternal.forJsonPath("$.stringKey").isNotPresent().test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`isTrue` returns true when true val")
    void istrue_returns_true_when_true_val() {
        assertThat(JsonPathInternal.forJsonPath("$.trueKey").isTrue().test(defaultRequestViewJson()))
                .isTrue();
    }

    @Test
    @DisplayName("`isTrue` returns false when false val")
    void istrue_returns_false_when_false_val() {
        assertThat(JsonPathInternal.forJsonPath("$.falseKey").isTrue().test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`isTrue` returns false when null val")
    void istrue_returns_false_when_null_val() {
        assertThat(JsonPathInternal.forJsonPath("$.nullKey").isTrue().test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`isFalse` returns true when false val")
    void isfalse_returns_true_when_false_val() {
        assertThat(JsonPathInternal.forJsonPath("$.falseKey").isFalse().test(defaultRequestViewJson()))
                .isTrue();
    }

    @Test
    @DisplayName("`isFalse` returns false when true val")
    void isfalse_returns_false_when_true_val() {
        assertThat(JsonPathInternal.forJsonPath("$.trueKey").isFalse().test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`isFalse` returns false when null val")
    void isfalse_returns_false_when_null_val() {
        assertThat(JsonPathInternal.forJsonPath("$.nullKey").isFalse().test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`isNull` returns true when null val")
    void isnull_returns_true_when_null_val() {
        assertThat(JsonPathInternal.forJsonPath("$.nullKey").isNull().test(defaultRequestViewJson()))
                .isTrue();
    }

    @Test
    @DisplayName("`isNull` returns false when val not present")
    void isnull_returns_false_when_val_not_present() {
        assertThat(JsonPathInternal.forJsonPath("$.notPresentVal").isNull().test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`isNull` returns false when true val")
    void isnull_returns_false_when_true_val() {
        assertThat(JsonPathInternal.forJsonPath("$.trueKey").isNull().test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`isNull` returns false when false val")
    void isnull_returns_false_when_false_val() {
        assertThat(JsonPathInternal.forJsonPath("$.falseKey").isNull().test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`isNull` returns false when string val")
    void isnull_returns_false_when_string_val() {
        assertThat(JsonPathInternal.forJsonPath("$.stringKey").isNull().test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`matches` returns true when string val matches")
    void matches_returns_true_when_string_val_matches() {
        assertThat(JsonPathInternal.forJsonPath("$.stringKey").matches("str.*").test(defaultRequestViewJson()))
                .isTrue();
    }

    @Test
    @DisplayName("`matches` returns false when string val not matches")
    void matches_returns_false_when_string_val_not_matches() {
        assertThat(JsonPathInternal.forJsonPath("$.stringKey").matches("strr.*").test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`matches` returns false when null val")
    void matches_returns_false_when_null_val() {
        assertThat(JsonPathInternal.forJsonPath("$.nullKey").matches("strr.*").test(defaultRequestViewJson()))
                .isFalse();
        assertThat(JsonPathInternal.forJsonPath("$.nullKey").matches("null").test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`matches` returns false when true val")
    void matches_returns_false_when_true_val() {
        assertThat(JsonPathInternal.forJsonPath("$.trueKey").matches("true").test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`numberEquals` returns true when number val equals")
    void numberequals_returns_true_when_number_val_equals() {
        assertThat(JsonPathInternal.forJsonPath("$.intKey")
                        .numberEquals(1234567)
                        .test(defaultRequestViewJson()))
                .isTrue();
        assertThat(JsonPathInternal.forJsonPath("$.longKey")
                        .numberEquals(1234444444444444444L)
                        .test(defaultRequestViewJson()))
                .isTrue();
    }

    @Test
    @DisplayName("`numberEquals` returns false when double val")
    void numberequals_returns_false_when_double_val() {
        assertThat(JsonPathInternal.forJsonPath("$.doubleKey")
                        .numberEquals(123456)
                        .test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`numberEquals` returns false when val not presentt")
    void numberequals_returns_false_when_val_not_presentt() {
        assertThat(JsonPathInternal.forJsonPath("$.notPresentVal")
                        .numberEquals(123456)
                        .test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`numberEquals` returns false when null val")
    void numberequals_returns_false_when_null_val() {
        assertThat(JsonPathInternal.forJsonPath("$.nullKey")
                        .numberEquals(123456)
                        .test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`decimalEquals` returns true when double val")
    void decimalequals_returns_true_when_double_val() {
        assertThat(JsonPathInternal.forJsonPath("$.doubleKey")
                        .decimalEquals(new BigDecimal("123.456"))
                        .test(defaultRequestViewJson()))
                .isTrue();
    }

    @Test
    @DisplayName("`decimalEquals` returns true when decimal val")
    void decimalequals_returns_true_when_decimal_val() {
        assertThat(JsonPathInternal.forJsonPath("$.decimalKey")
                        .decimalEquals(new BigDecimal("123.45623123123456789"))
                        .test(defaultRequestViewJson()))
                .isTrue();
    }

    @Test
    @DisplayName("`decimalEquals` returns false when decimal val not equal")
    void decimalequals_returns_false_when_decimal_val_not_equal() {
        assertThat(JsonPathInternal.forJsonPath("$.doubleKey")
                        .decimalEquals(new BigDecimal("123.4566"))
                        .test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`decimalEquals` returns false when null val")
    void decimalequals_returns_false_when_null_val() {
        assertThat(JsonPathInternal.forJsonPath("$.nullKey")
                        .decimalEquals(new BigDecimal("123.4566"))
                        .test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`decimalEquals` returns false when val not present")
    void decimalequals_returns_false_when_val_not_present() {
        assertThat(JsonPathInternal.forJsonPath("$.notPresentVal")
                        .decimalEquals(new BigDecimal("123.4566"))
                        .test(defaultRequestViewJson()))
                .isFalse();
    }

    @Test
    @DisplayName("`1234567` returns true when number val equals")
    void returns_true_when_number_val_equals() {
        assertThat(JsonPathInternal.forJsonPath("$.intKey")
                        .decimalEquals(BigDecimal.valueOf(1234567))
                        .test(defaultRequestViewJson()))
                .isTrue();
        assertThat(JsonPathInternal.forJsonPath("$.longKey")
                        .decimalEquals(BigDecimal.valueOf(1234444444444444444L))
                        .test(defaultRequestViewJson()))
                .isTrue();
    }

    private ImpRequestView requestViewJson(@Language("json") String json) {
        try {
            return new ImpRequestView(
                    ImpMethod.GET, Map.of(), () -> json.getBytes(StandardCharsets.UTF_8), new URI("/"));
        } catch (URISyntaxException e) {
            return BaseTest.hide(e);
        }
    }

    private ImpRequestView defaultRequestViewJson() {
        return requestViewJson(defaultJson);
    }
}
