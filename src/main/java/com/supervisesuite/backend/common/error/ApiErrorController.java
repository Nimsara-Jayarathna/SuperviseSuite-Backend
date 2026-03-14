package com.supervisesuite.backend.common.error;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Unifies framework fallback errors under the same API error envelope.
 *
 * <p>This controller handles requests that reach Spring Boot's {@code /error}
 * fallback path (for example, unmapped routes not already handled by MVC advice).
 */
@Controller
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ApiErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;
    private final ApiResponseFactory apiResponseFactory;

    public ApiErrorController(ErrorAttributes errorAttributes, ApiResponseFactory apiResponseFactory) {
        this.errorAttributes = errorAttributes;
        this.apiResponseFactory = apiResponseFactory;
    }

    @RequestMapping
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        ErrorAttributeOptions options = ErrorAttributeOptions.defaults();
        var attributes = errorAttributes.getErrorAttributes(new ServletWebRequest(request), options);

        int statusCode = extractStatusCode(request, attributes.get("status"));
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String path = String.valueOf(attributes.getOrDefault("path", request.getRequestURI()));
        String message = defaultMessage(status, attributes.get("message"));
        ErrorCode code = mapCode(status);

        ApiResponse<Void> body = apiResponseFactory.errorBody(
            status,
            code,
            message,
            List.of(),
            path,
            null
        );

        return ResponseEntity.status(status).body(body);
    }

    private int extractStatusCode(HttpServletRequest request, Object statusAttr) {
        if (statusAttr instanceof Integer status) {
            return status;
        }
        Object fallback = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (fallback instanceof Integer status) {
            return status;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String defaultMessage(HttpStatus status, Object rawMessage) {
        if (rawMessage instanceof String message && !message.isBlank()) {
            return message;
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "Resource not found.";
        }
        if (status == HttpStatus.METHOD_NOT_ALLOWED) {
            return "HTTP method is not supported for this endpoint.";
        }
        if (status == HttpStatus.UNSUPPORTED_MEDIA_TYPE) {
            return "Unsupported media type.";
        }
        if (status == HttpStatus.NOT_ACCEPTABLE) {
            return "Requested response media type is not acceptable.";
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return "Bad request.";
        }
        return "An unexpected error occurred.";
    }

    private ErrorCode mapCode(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return ErrorCode.UNAUTHORIZED;
        }
        if (status == HttpStatus.FORBIDDEN) {
            return ErrorCode.FORBIDDEN;
        }
        if (status == HttpStatus.NOT_FOUND) {
            return ErrorCode.NOT_FOUND;
        }
        if (status == HttpStatus.CONFLICT) {
            return ErrorCode.CONFLICT;
        }
        if (status == HttpStatus.BAD_REQUEST
            || status == HttpStatus.METHOD_NOT_ALLOWED
            || status == HttpStatus.UNSUPPORTED_MEDIA_TYPE
            || status == HttpStatus.NOT_ACCEPTABLE) {
            return ErrorCode.BAD_REQUEST;
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}
