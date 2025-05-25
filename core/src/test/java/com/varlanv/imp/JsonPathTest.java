package com.varlanv.imp;

import static org.assertj.core.api.Assertions.*;

import com.varlanv.imp.commontest.FastTest;
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
            "decimalKey": 123.456,
            "numberKey": 1234567890,
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
}
