package com.supervisesuite.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalizationUtilsTest {

    @Test
    void trimToNull_null_returnsNull() {
        assertThat(NormalizationUtils.trimToNull(null)).isNull();
    }

    @Test
    void trimToNull_blank_returnsNull() {
        assertThat(NormalizationUtils.trimToNull("   ")).isNull();
    }

    @Test
    void trimToNull_trims() {
        assertThat(NormalizationUtils.trimToNull("  hello  ")).isEqualTo("hello");
    }

    @Test
    void defaultIfBlank_blank_returnsFallback() {
        assertThat(NormalizationUtils.defaultIfBlank("   ", "fallback")).isEqualTo("fallback");
    }

    @Test
    void defaultIfBlank_value_returnsTrimmedValue() {
        assertThat(NormalizationUtils.defaultIfBlank("  value  ", "fallback")).isEqualTo("value");
    }

    @Test
    void normalizeRegistrationNumber_trimsAndUppercases() {
        assertThat(NormalizationUtils.normalizeRegistrationNumber("  it24100400  "))
            .isEqualTo("IT24100400");
    }

    @Test
    void normalizeRegistrationNumber_null_returnsNull() {
        assertThat(NormalizationUtils.normalizeRegistrationNumber(null)).isNull();
    }

    @Test
    void normalizeEmail_trimsAndLowercases() {
        assertThat(NormalizationUtils.normalizeEmail("  Alice@Example.COM  "))
            .isEqualTo("alice@example.com");
    }

    @Test
    void normalizeEmail_null_returnsNull() {
        assertThat(NormalizationUtils.normalizeEmail(null)).isNull();
    }
}
