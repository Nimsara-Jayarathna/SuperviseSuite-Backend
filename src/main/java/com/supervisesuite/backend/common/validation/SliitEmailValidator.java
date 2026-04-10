package com.supervisesuite.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementation of the {@link SliitEmail} constraint.
 *
 * <p>Validates that the normalized email ends with {@code @sliit.lk} and does
 * not end with {@code @my.sliit.lk}.
 *
 * <p>Returns {@code true} immediately for {@code null} or blank input so that
 * {@code @NotBlank} can own those messages and duplicate violations are avoided.
 */
public class SliitEmailValidator implements ConstraintValidator<SliitEmail, String> {

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.isBlank()) {
            // @NotBlank handles the null/blank case; skip here to avoid duplicate messages.
            return true;
        }

        String normalizedEmail = email.trim().toLowerCase();

        return normalizedEmail.endsWith("@sliit.lk")
            && !normalizedEmail.endsWith("@my.sliit.lk");
    }
}
