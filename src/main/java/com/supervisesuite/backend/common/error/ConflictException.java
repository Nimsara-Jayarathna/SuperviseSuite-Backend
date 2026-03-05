package com.supervisesuite.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a uniqueness constraint or state conflict is detected.
 *
 * <p>Maps to {@code HTTP 409 Conflict} with error code {@code CONFLICT}.
 * Use this instead of constructing {@link DomainException} with
 * {@link ErrorCode#CONFLICT} directly.
 *
 * <p>Common use cases:
 * <ul>
 *   <li>Duplicate email on registration</li>
 *   <li>Duplicate registration number on registration</li>
 * </ul>
 *
 * <p>Handled by
 * {@link GlobalExceptionHandler#handleDomainException} which returns a
 * structured {@link ApiError} response.
 */
public class ConflictException extends DomainException {

    /**
     * @param message a human-readable description of the conflict,
     *                safe to surface directly to the API consumer
     */
    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}
