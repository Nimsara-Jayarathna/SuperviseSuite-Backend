package com.supervisesuite.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an external dependency is temporarily unavailable.
 */
public class ServiceUnavailableException extends DomainException {

    public ServiceUnavailableException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}