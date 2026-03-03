package com.supervisesuite.backend.common.error;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Centralized exception handler for all REST controllers.
 *
 * <p><b>Security principle:</b> raw exception messages from framework or infrastructure
 * exceptions (Spring, Hibernate, Jackson) are never forwarded to API responses — only
 * fixed, safe strings are used. This prevents leaking internal class names, SQL
 * constraint names, or stack details to callers.
 *
 * <p>Messages from our own {@link DomainException} subclasses are considered safe to
 * surface because they are authored in application code with the API consumer in mind.
 */
@Slf4j
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
            "Validation failed.",
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
            "Validation failed.",
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
            "Malformed request body.",
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
            "Authentication required.",
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
            "Access denied.",
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
            "Resource not found.",
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
        log.warn("Data integrity violation on [{}]: {}", request.getRequestURI(), exception.getMessage());
        ApiError error = buildError(
            ErrorCode.CONFLICT,
            HttpStatus.CONFLICT,
            "Data conflict detected.",
            request,
            List.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidationException(
        ValidationException exception,
        HttpServletRequest request
    ) {
        ApiError error = buildError(
            ErrorCode.VALIDATION_ERROR,
            HttpStatus.BAD_REQUEST,
            safeMessage(exception.getMessage(), "Validation failed."),
            request,
            exception.getDetails()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
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
        log.error("Unhandled exception on [{}]: {}", request.getRequestURI(), exception.getMessage(), exception);
        ApiError error = buildError(
            ErrorCode.INTERNAL_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred.",
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
        return error;
    }

    private String safeMessage(String rawMessage, String fallback) {
        if (Objects.nonNull(rawMessage) && !rawMessage.isBlank()) {
            return rawMessage;
        }
        return fallback;
    }
}
