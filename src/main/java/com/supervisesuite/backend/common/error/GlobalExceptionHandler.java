package com.supervisesuite.backend.common.error;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

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
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ApiResponseFactory apiResponseFactory;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        List<ApiErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.add(new ApiErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()));
        }

        return errorResponse(
            ErrorCode.VALIDATION_ERROR,
            HttpStatus.BAD_REQUEST,
            "Validation failed.",
            request,
            details
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        List<ApiErrorDetail> details = exception
            .getConstraintViolations()
            .stream()
            .map(v -> new ApiErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
            .toList();

        return errorResponse(
            ErrorCode.VALIDATION_ERROR,
            HttpStatus.BAD_REQUEST,
            "Validation failed.",
            request,
            details
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ErrorCode.BAD_REQUEST,
            HttpStatus.BAD_REQUEST,
            "Malformed request body.",
            request,
            List.of()
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(
        AuthenticationException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ErrorCode.UNAUTHORIZED,
            HttpStatus.UNAUTHORIZED,
            "Authentication required.",
            request,
            List.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
        AccessDeniedException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ErrorCode.FORBIDDEN,
            HttpStatus.FORBIDDEN,
            "Access denied.",
            request,
            List.of()
        );
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(
        EntityNotFoundException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ErrorCode.NOT_FOUND,
            HttpStatus.NOT_FOUND,
            "Resource not found.",
            request,
            List.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
        DataIntegrityViolationException exception,
        HttpServletRequest request
    ) {
        log.warn("Data integrity violation on [{}]: {}", request.getRequestURI(), exception.getMessage());
        return errorResponse(
            ErrorCode.CONFLICT,
            HttpStatus.CONFLICT,
            "Data conflict detected.",
            request,
            List.of()
        );
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
        ValidationException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ErrorCode.VALIDATION_ERROR,
            HttpStatus.BAD_REQUEST,
            safeMessage(exception.getMessage(), "Validation failed."),
            request,
            exception.getDetails()
        );
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(
        DomainException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            exception.getCode(),
            exception.getStatus(),
            safeMessage(exception.getMessage(), "Domain error."),
            request,
            List.of()
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(
        MissingServletRequestParameterException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ErrorCode.VALIDATION_ERROR,
            HttpStatus.BAD_REQUEST,
            "Validation failed.",
            request,
            List.of(new ApiErrorDetail(exception.getParameterName(), "Request parameter is required."))
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
        HttpRequestMethodNotSupportedException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ErrorCode.BAD_REQUEST,
            HttpStatus.METHOD_NOT_ALLOWED,
            "HTTP method is not supported for this endpoint.",
            request,
            List.of()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
        HttpMediaTypeNotSupportedException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ErrorCode.BAD_REQUEST,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported media type.",
            request,
            List.of()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotAcceptable(
        HttpMediaTypeNotAcceptableException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ErrorCode.BAD_REQUEST,
            HttpStatus.NOT_ACCEPTABLE,
            "Requested response media type is not acceptable.",
            request,
            List.of()
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(
        NoHandlerFoundException exception,
        HttpServletRequest request
    ) {
        return errorResponse(
            ErrorCode.NOT_FOUND,
            HttpStatus.NOT_FOUND,
            "Resource not found.",
            request,
            List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("Unhandled exception on [{}]: {}", request.getRequestURI(), exception.getMessage(), exception);
        return errorResponse(
            ErrorCode.INTERNAL_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred.",
            request,
            List.of()
        );
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(
        ErrorCode code,
        HttpStatus status,
        String message,
        HttpServletRequest request,
        List<ApiErrorDetail> details
    ) {
        return apiResponseFactory.error(status, code, message, details, request);
    }

    private String safeMessage(String rawMessage, String fallback) {
        if (Objects.nonNull(rawMessage) && !rawMessage.isBlank()) {
            return rawMessage;
        }
        return fallback;
    }
}
