package com.supervisesuite.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 8;

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
