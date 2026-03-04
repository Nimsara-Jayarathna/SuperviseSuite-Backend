package com.supervisesuite.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.error.ApiError;
import com.supervisesuite.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Writes a structured {@link ApiError} JSON response directly to the HTTP servlet
 * response, bypassing the Spring MVC dispatcher.
 *
 * <p>Needed by {@link SecurityConfig} because Spring Security's
 * {@code AuthenticationEntryPoint} and {@code AccessDeniedHandler} fire outside
 * the MVC filter chain, so {@link com.supervisesuite.backend.common.error.GlobalExceptionHandler}
 * is never invoked for these cases. Without this component, {@link SecurityConfig}
 * would have to build {@link ApiError} objects inline — duplicating error-construction
 * logic that belongs to the error layer.
 *
 * <p>Single Responsibility: this class owns exactly <em>one</em> concern —
 * serialising a typed error response for a security failure.
 */
@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Builds an {@link ApiError} with the supplied fields and writes it as JSON
     * to the given response.
     *
     * @param response the HTTP response to write to
     * @param status   the HTTP status code (e.g. 401, 403)
     * @param error    the reason phrase (e.g. "Unauthorized")
     * @param code     the application error code
     * @param message  the human-readable message safe to surface to the client
     * @param path     the request URI
     * @throws IOException if writing to the response stream fails
     */
    public void write(
        HttpServletResponse response,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path
    ) throws IOException {
        ApiError apiError = buildApiError(status, error, code, message, path);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), apiError);
    }

    private ApiError buildApiError(
        int status,
        String error,
        ErrorCode code,
        String message,
        String path
    ) {
        ApiError apiError = new ApiError();
        apiError.setTimestamp(Instant.now());
        apiError.setStatus(status);
        apiError.setError(error);
        apiError.setCode(code.name());
        apiError.setMessage(message);
        apiError.setPath(path);
        apiError.setTraceId(null);
        apiError.setDetails(List.of());
        return apiError;
    }
}
