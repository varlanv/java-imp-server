package com.varlanv.imp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.varlanv.imp.commontest.FastTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImpMethodTest implements FastTest {

    @Test
    @DisplayName("ofStrict should fail with error if unknown status code")
    void ofstrict_should_fail_with_error_if_unknown_status_code() {
        assertThatThrownBy(() -> ImpMethod.ofStrict("qwe"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Internal error in ImpServer - failed to parse HTTP method [ qwe ]");
    }

    @Test
    @DisplayName("ofStrict should return correct status")
    void ofstrict_should_return_correct_status() {
        assertThat(ImpMethod.ofStrict("GET")).isSameAs(ImpMethod.GET);
    }
}
