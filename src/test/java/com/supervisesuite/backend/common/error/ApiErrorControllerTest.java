package com.supervisesuite.backend.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ApiErrorControllerTest {

    @Mock
    private ErrorAttributes errorAttributes;

    private ApiErrorController controller;

    @BeforeEach
    void setUp() {
        controller = new ApiErrorController(errorAttributes, new ApiResponseFactory());
    }

    @Test
    void error_notFound_mapsToNotFoundEnvelope() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unknown");
        when(errorAttributes.getErrorAttributes(any(), any()))
            .thenReturn(Map.of("status", 404, "path", "/unknown"));

        ResponseEntity<ApiResponse<Void>> response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("Resource not found.");
    }

    @Test
    void error_methodNotAllowed_mapsToBadRequestCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/example");
        when(errorAttributes.getErrorAttributes(any(), any()))
            .thenReturn(Map.of("status", 405, "path", "/api/example"));

        ResponseEntity<ApiResponse<Void>> response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().getMessage()).isEqualTo("HTTP method is not supported for this endpoint.");
    }

    @Test
    void error_tooManyRequests_mapsToTooManyRequestsCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/projects");
        when(errorAttributes.getErrorAttributes(any(), any()))
            .thenReturn(Map.of("status", 429, "path", "/api/student/projects"));

        ResponseEntity<ApiResponse<Void>> response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void error_serviceUnavailable_mapsToServiceUnavailableCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/register/config");
        when(errorAttributes.getErrorAttributes(any(), any()))
            .thenReturn(Map.of("status", 503, "path", "/api/auth/register/config"));

        ResponseEntity<ApiResponse<Void>> response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("SERVICE_UNAVAILABLE");
    }
}
