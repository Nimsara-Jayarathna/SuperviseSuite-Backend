package com.supervisesuite.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a uniqueness or state conflict is detected (HTTP 409).
 * Use instead of constructing DomainException(CONFLICT, ...) directly.
 */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}
