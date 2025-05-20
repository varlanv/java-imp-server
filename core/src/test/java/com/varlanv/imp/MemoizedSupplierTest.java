package com.varlanv.imp;

import com.varlanv.imp.commontest.FastTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MemoizedSupplierTest implements FastTest {

    @Test
    @DisplayName("should return same value when read multiple times")
    void should_return_same_value_when_read_multiple_times() {
        var atomicInteger = new AtomicInteger();
        var subject = MemoizedSupplier.of(atomicInteger::incrementAndGet);

        assertThat(subject.get()).isEqualTo(1);
        assertThat(subject.get()).isEqualTo(1);
        assertThat(subject.get()).isEqualTo(1);
        assertThat(subject.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("should fail if inner supplier returns null")
    void should_fail_if_inner_supplier_returns_null() {
        @SuppressWarnings("all")
        var subject = MemoizedSupplier.of(() -> null);

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(subject::get)
            .withMessage("Supplier returned null");
    }
}
