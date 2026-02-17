package com.supervisesuite.backend.common.error;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        List<ApiErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.add(new ApiErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()));
        }

        ApiError error = buildError(
            ErrorCode.VALIDATION_ERROR,
            HttpStatus.BAD_REQUEST,
            safeMessage(exception.getMessage(), "Validation failed."),
            request,
            details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        List<ApiErrorDetail> details = exception
            .getConstraintViolations()
            .stream()
            .map(v -> new ApiErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
            .toList();

        ApiError error = buildError(
            ErrorCode.VALIDATION_ERROR,
            HttpStatus.BAD_REQUEST,
            safeMessage(exception.getMessage(), "Validation failed."),
            request,
            details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMessageNotReadable(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        ApiError error = buildError(
            ErrorCode.BAD_REQUEST,
            HttpStatus.BAD_REQUEST,
            safeMessage(exception.getMessage(), "Malformed request body."),
            request,
            List.of()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
        AuthenticationException exception,
        HttpServletRequest request
    ) {
        ApiError error = buildError(
            ErrorCode.UNAUTHORIZED,
            HttpStatus.UNAUTHORIZED,
            safeMessage(exception.getMessage(), "Authentication required."),
            request,
            List.of()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
        AccessDeniedException exception,
        HttpServletRequest request
    ) {
        ApiError error = buildError(
            ErrorCode.FORBIDDEN,
            HttpStatus.FORBIDDEN,
            safeMessage(exception.getMessage(), "Access denied."),
            request,
            List.of()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(
        EntityNotFoundException exception,
        HttpServletRequest request
    ) {
        ApiError error = buildError(
            ErrorCode.NOT_FOUND,
            HttpStatus.NOT_FOUND,
            safeMessage(exception.getMessage(), "Resource not found."),
            request,
            List.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
        DataIntegrityViolationException exception,
        HttpServletRequest request
    ) {
        ApiError error = buildError(
            ErrorCode.CONFLICT,
            HttpStatus.CONFLICT,
            safeMessage(exception.getMessage(), "Data conflict detected."),
            request,
            List.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomainException(DomainException exception, HttpServletRequest request) {
        ApiError error = buildError(
            exception.getCode(),
            exception.getStatus(),
            safeMessage(exception.getMessage(), "Domain error."),
            request,
            List.of()
        );
        return ResponseEntity.status(exception.getStatus()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception exception, HttpServletRequest request) {
        ApiError error = buildError(
            ErrorCode.INTERNAL_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR,
            safeMessage(exception.getMessage(), "An unexpected error occurred."),
            request,
            List.of()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private ApiError buildError(
        ErrorCode code,
        HttpStatus status,
        String message,
        HttpServletRequest request,
        List<ApiErrorDetail> details
    ) {
        ApiError error = new ApiError();
        error.setTimestamp(Instant.now());
        error.setStatus(status.value());
        error.setError(status.getReasonPhrase());
        error.setCode(code.name());
        error.setMessage(message);
        error.setPath(request.getRequestURI());
        error.setTraceId(null);
        error.setDetails(details == null ? List.of() : details);

        // TODO: Populate traceId using correlation-id from filter/MDC.
        // TODO: Avoid leaking internal exception messages in production profiles.
        // TODO: Add structured logging with error code + traceId.

        return error;
    }

    private String safeMessage(String rawMessage, String fallback) {
        if (Objects.nonNull(rawMessage) && !rawMessage.isBlank()) {
            return rawMessage;
        }
        return fallback;
    }
}
