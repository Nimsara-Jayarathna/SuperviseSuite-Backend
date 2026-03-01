package com.supervisesuite.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a password meets the application's minimum strength requirements.
 *
 * <p>Rules enforced by {@link StrongPasswordValidator}:
 * <ul>
 *   <li>At least 8 characters</li>
 *   <li>At least one uppercase letter</li>
 *   <li>At least one lowercase letter</li>
 *   <li>At least one digit</li>
 *   <li>At least one special (non-alphanumeric) character</li>
 * </ul>
 *
 * <p>When validation fails the constraint message lists exactly which rules are
 * missing, e.g.: {@code "Password must contain: a digit, a special character."}.
 *
 * <p>This annotation is intentionally lenient on {@code null} and blank values —
 * pair it with {@code @NotBlank} to reject those cases separately.
 *
 * @see StrongPasswordValidator
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a digit, and a special character.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
