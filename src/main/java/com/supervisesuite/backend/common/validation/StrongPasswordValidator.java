package com.supervisesuite.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementation of the {@link StrongPassword} constraint.
 *
 * <p>Passphrase policy is intentionally simple: enforce a minimum length only.
 *
 * <p>Returns {@code true} immediately for {@code null} or blank input so that
 * {@code @NotBlank} can own those messages and duplicate violations are avoided.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 12;

    /**
     * Checks all password strength rules and builds a combined violation message
     * if any rules are not satisfied.
     *
     * @param password the candidate password string
     * @param context  the constraint validator context used to build custom messages
     * @return {@code true} if the password satisfies all rules or is null/blank;
     *         {@code false} otherwise
     */
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            // @NotBlank handles the null/blank case; skip here to avoid duplicate messages.
            return true;
        }

        if (password.length() < MIN_LENGTH) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Password must be at least " + MIN_LENGTH + " characters.")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
