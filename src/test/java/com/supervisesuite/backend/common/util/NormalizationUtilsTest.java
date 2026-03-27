package com.supervisesuite.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalizationUtilsTest {

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
