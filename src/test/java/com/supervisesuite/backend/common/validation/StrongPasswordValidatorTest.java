package com.supervisesuite.backend.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @Test
    void isValid_blankPassword_returnsTrue() {
        assertThat(validator.isValid("   ", context)).isTrue();
    }

    @Test
    void isValid_strongPassword_returnsTrue() {
        assertThat(validator.isValid("my dog loves eating pizza", context)).isTrue();
    }

    @Test
    void isValid_weakPassword_returnsFalseAndBuildsCombinedViolation() {
        when(context.buildConstraintViolationWithTemplate(org.mockito.ArgumentMatchers.contains("at least 12 characters")))
            .thenReturn(violationBuilder);

        boolean valid = validator.isValid("abc", context);

        assertThat(valid).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(violationBuilder).addConstraintViolation();
    }
}
