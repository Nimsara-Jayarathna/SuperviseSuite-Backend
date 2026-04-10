package com.supervisesuite.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that an email is a valid SLIIT institutional supervisor email.
 *
 * <p>Accepted domain: {@code @sliit.lk}
 *
 * <p>Excluded domain: {@code @my.sliit.lk} (student portal domain)
 *
 * <p>This annotation is intentionally lenient on {@code null} and blank values —
 * pair it with {@code @NotBlank} to reject those cases separately.
 *
 * @see SliitEmailValidator
 */
@Documented
@Constraint(validatedBy = SliitEmailValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SliitEmail {

    String message() default "Email must be a valid SLIIT institutional email (@sliit.lk).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
