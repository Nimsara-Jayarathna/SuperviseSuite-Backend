package com.supervisesuite.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the {@link StrongPassword} constraint.
 *
 * <p>Evaluates all strength rules independently and, if any fail, builds a single
 * constraint violation message that lists every missing requirement. This avoids
 * forcing the user to submit multiple times to discover all issues at once.
 *
 * <p>Returns {@code true} immediately for {@code null} or blank input so that
 * {@code @NotBlank} can own those messages and duplicate violations are avoided.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 8;

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

        List<String> violations = new ArrayList<>();

        if (password.length() < MIN_LENGTH) {
            violations.add("at least " + MIN_LENGTH + " characters");
        }
        if (!password.chars().anyMatch(Character::isUpperCase)) {
            violations.add("an uppercase letter");
        }
        if (!password.chars().anyMatch(Character::isLowerCase)) {
            violations.add("a lowercase letter");
        }
        if (!password.chars().anyMatch(Character::isDigit)) {
            violations.add("a digit");
        }
        if (!password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            violations.add("a special character");
        }

        if (violations.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
            "Password must contain: " + String.join(", ", violations) + "."
        ).addConstraintViolation();

        return false;
    }
}
