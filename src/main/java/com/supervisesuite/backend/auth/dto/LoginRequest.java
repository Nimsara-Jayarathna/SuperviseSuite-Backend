package com.supervisesuite.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Inbound payload for {@code POST /api/auth/login}.
 *
 * <p>Both fields are required. Bean Validation is enforced at the controller layer;
 * any violation produces a {@code 400 VALIDATION_ERROR} response.
 */
public class LoginRequest {

    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be a valid email address.")
    private String email;

    @NotBlank(message = "Password is required.")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
