package com.supervisesuite.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a password meets the application's minimum passphrase length.
 *
 * <p>Rule enforced by {@link StrongPasswordValidator}: at least 12 characters.
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

    String message() default "Password must be at least 12 characters.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
