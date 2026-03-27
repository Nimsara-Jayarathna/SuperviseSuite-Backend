package com.supervisesuite.backend.common.api;

import com.supervisesuite.backend.common.error.ApiErrorDetail;
import com.supervisesuite.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ApiResponseFactory {

    public <T> ResponseEntity<ApiResponse<T>> ok(String message, T data, HttpServletRequest request) {
        return ResponseEntity.ok(successBody(message, data, request));
    }

    public <T> ResponseEntity<ApiResponse<T>> created(String message, T data, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(successBody(message, data, request));
    }

    public <T> ApiResponse<T> successBody(String message, T data, HttpServletRequest request) {
        return new ApiResponse<>(true, message, data, null, buildMeta(request));
    }

    public ResponseEntity<ApiResponse<Void>> error(
        HttpStatus status,
        ErrorCode code,
        String message,
        List<ApiErrorDetail> details,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(errorBody(status, code, message, details, request));
    }

    public ApiResponse<Void> errorBody(
        HttpStatus status,
        ErrorCode code,
        String message,
        List<ApiErrorDetail> details,
        HttpServletRequest request
    ) {
        return errorBody(status, code, message, details, request.getRequestURI(), resolveTraceId(request));
    }

    public ApiResponse<Void> errorBody(
        HttpStatus status,
        ErrorCode code,
        String message,
        List<ApiErrorDetail> details,
        String path,
        String traceId
    ) {
        ApiErrorBody errorBody = new ApiErrorBody(code.name(), status.value(), details);
        ApiMeta meta = new ApiMeta(Instant.now(), path, traceId);
        return new ApiResponse<>(false, message, null, errorBody, meta);
    }

    private ApiMeta buildMeta(HttpServletRequest request) {
        return new ApiMeta(Instant.now(), request.getRequestURI(), resolveTraceId(request));
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object traceIdAttr = request.getAttribute("traceId");
        if (traceIdAttr instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String traceIdHeader = request.getHeader("X-Trace-Id");
        return traceIdHeader == null || traceIdHeader.isBlank() ? null : traceIdHeader;
    }
}
