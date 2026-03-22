package com.supervisesuite.backend.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new ApiResponseFactory());
        request = new MockHttpServletRequest("GET", "/api/test");
    }

    @Test
    void handleValidationException_returnsBadRequestWithDetails() {
        ValidationException exception = new ValidationException("repositoryUrl", "Repository URL is required.");

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getError().getDetails()).hasSize(1);
    }

    @Test
    void handleDomainException_usesExceptionStatusAndCode() {
        DomainException exception = new DomainException(
            ErrorCode.CONFLICT,
            HttpStatus.CONFLICT,
            "Conflict happened."
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleDomainException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("CONFLICT");
        assertThat(response.getBody().getMessage()).isEqualTo("Conflict happened.");
    }

    @Test
    void handleGenericException_returnsInternalErrorEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(
            new RuntimeException("boom"),
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getError().getDetails()).isEqualTo(List.of());
    }
}
