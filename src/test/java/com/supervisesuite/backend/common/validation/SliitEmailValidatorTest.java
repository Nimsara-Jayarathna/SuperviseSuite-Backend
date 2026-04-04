package com.supervisesuite.backend.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SliitEmailValidatorTest {

    private final SliitEmailValidator validator = new SliitEmailValidator();

    @ParameterizedTest(name = "[{index}] input={0} => valid={1} ({2})")
    @CsvSource(
        value = {
            "john@sliit.lk, true, correct supervisor domain",
            "JOHN@SLIIT.LK, true, uppercase normalization",
            "it24100400@my.sliit.lk, false, student portal domain",
            "john@gmail.com, false, non SLIIT domain",
            "john@fakesliit.lk, false, does not end with @sliit.lk",
            "NULL, true, null passthrough to NotBlank",
            "'', true, empty string passthrough to NotBlank"
        },
        nullValues = "NULL"
    )
    void isValid_domainRules_appliedAsExpected(String input, boolean expected, String reason) {
        boolean actual = validator.isValid(input, null);

        assertThat(actual)
            .as(reason)
            .isEqualTo(expected);
    }
}
