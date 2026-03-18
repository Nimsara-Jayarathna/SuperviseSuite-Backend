package com.supervisesuite.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Writes the unified API error envelope directly to the HTTP servlet
 * response, bypassing the Spring MVC dispatcher.
 *
 * <p>Needed by {@link SecurityConfig} because Spring Security's
 * {@code AuthenticationEntryPoint} and {@code AccessDeniedHandler} fire outside
 * the MVC filter chain, so {@link com.supervisesuite.backend.common.error.GlobalExceptionHandler}
 * is never invoked for these cases. Without this component, {@link SecurityConfig}
 * would have to build error envelopes inline — duplicating error-construction
 * logic that belongs to the error layer.
 *
 * <p>Single Responsibility: this class owns exactly <em>one</em> concern —
 * serialising a typed error response for a security failure.
 */
@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final ApiResponseFactory apiResponseFactory;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper, ApiResponseFactory apiResponseFactory) {
        this.objectMapper = objectMapper;
        this.apiResponseFactory = apiResponseFactory;
    }

    /**
     * Builds a unified API error response and writes it as JSON.
     *
     * @param request  current HTTP request (used for meta.path/trace resolution)
     * @param response HTTP response to write to
     * @param status   HTTP status code (for example 401/403)
     * @param code     application error code
     * @param message  client-safe message
     * @throws IOException if writing to the response stream fails
     */
    public void write(
        HttpServletRequest request,
        HttpServletResponse response,
        HttpStatus status,
        ErrorCode code,
        String message
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        ApiResponse<Void> apiErrorResponse = apiResponseFactory.errorBody(
            status,
            code,
            message,
            java.util.List.of(),
            request
        );

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), apiErrorResponse);
        response.flushBuffer();
    }
}
