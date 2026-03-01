package com.supervisesuite.backend.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown for programmatic validation failures that cannot be expressed
 * via Bean Validation annotations (HTTP 400 VALIDATION_ERROR).
 * Carries field-level details for the error response.
 */
public class ValidationException extends DomainException {

    private final List<ApiErrorDetail> details;

    public ValidationException(String message, List<ApiErrorDetail> details) {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ValidationException(String field, String issue) {
        this("Validation failed.", List.of(new ApiErrorDetail(field, issue)));
    }

    public List<ApiErrorDetail> getDetails() {
        return details;
    }
}
