package com.supervisesuite.backend.auth.controller;

import com.supervisesuite.backend.auth.dto.RegisterRequest;
import com.supervisesuite.backend.auth.dto.RegisterResponse;
import com.supervisesuite.backend.auth.service.AuthService;
import com.supervisesuite.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 *
 * <p>All routes under {@code /api/auth} are publicly accessible
 * (no JWT required) as configured in
 * {@link com.supervisesuite.backend.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new student account.
     *
     * <pre>
     * POST /api/auth/register
     * </pre>
     *
     * <p>Request body is validated via Bean Validation. Any constraint violation
     * produces a {@code 400 VALIDATION_ERROR} response with field-level details
     * before the service layer is reached.
     *
     * @param request the registration payload; must pass all validation constraints
     * @return {@code 201 Created} with an {@link ApiResponse} wrapping a
     *         {@link RegisterResponse} on success
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
        @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResponse data = authService.registerStudent(request);

        ApiResponse<RegisterResponse> response = new ApiResponse<>(
            true,
            "Registration successful.",
            data,
            null
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
