package com.supervisesuite.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when authentication fails — invalid credentials, missing token, etc.
 *
 * <p>Maps to {@code HTTP 401 Unauthorized} with error code {@code UNAUTHORIZED}.
 *
 * <p>Security note: messages must never distinguish between an unknown email and
 * a wrong password — always use a single generic message to prevent user enumeration.
 *
 * <p>Handled automatically by
 * {@link GlobalExceptionHandler#handleDomainException} which returns a
 * unified API error envelope.
 */
public class UnauthorizedException extends DomainException {

    /**
     * @param message a safe, generic message — must not reveal which credential was wrong
     */
    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
    }
}
