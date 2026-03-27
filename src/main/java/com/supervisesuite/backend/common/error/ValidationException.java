package com.supervisesuite.backend.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown for programmatic validation failures that cannot be expressed
 * through Bean Validation annotations.
 *
 * <p>Maps to {@code HTTP 400 Bad Request} with error code {@code VALIDATION_ERROR}.
 * Carries optional field-level {@link ApiErrorDetail} entries which are forwarded
 * to the {@code details[]} array in the API error response.
 *
 * <p>Prefer Bean Validation annotations ({@code @NotBlank}, {@code @Email}, etc.)
 * where possible. Use this exception for cross-field rules or business constraints
 * that require service-layer context.
 *
 * <p>Handled by
 * {@link GlobalExceptionHandler#handleValidationException} which propagates
 * {@code details[]} into the unified API error envelope.
 */
public class ValidationException extends DomainException {

    private final List<ApiErrorDetail> details;

    /**
     * Creates a validation exception with a custom summary message and one or more
     * field-level details.
     *
     * @param message human-readable summary of the validation failure
     * @param details list of {@link ApiErrorDetail} items; {@code null} is treated as empty
     */
    public ValidationException(String message, List<ApiErrorDetail> details) {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    /**
     * Convenience constructor for a single-field validation failure.
     *
     * @param field the name of the offending input field
     * @param issue the human-readable description of why the field is invalid
     */
    public ValidationException(String field, String issue) {
        this("Validation failed.", List.of(new ApiErrorDetail(field, issue)));
    }

    /**
     * Returns the field-level validation details to be included in the
     * {@code details[]} array of the API error response.
     *
     * @return immutable list of {@link ApiErrorDetail}; never {@code null}
     */
    public List<ApiErrorDetail> getDetails() {
        return details;
    }
}
