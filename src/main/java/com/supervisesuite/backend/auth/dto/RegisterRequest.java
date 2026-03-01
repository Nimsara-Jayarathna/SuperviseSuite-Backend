package com.supervisesuite.backend.auth.dto;

import com.supervisesuite.backend.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for {@code POST /api/auth/register}.
 *
 * <p>All fields are required. Bean Validation is enforced at the controller layer;
 * any violation produces a {@code 400 VALIDATION_ERROR} response with a populated
 * {@code details[]} array via {@link com.supervisesuite.backend.common.error.GlobalExceptionHandler}.
 */
public class RegisterRequest {

    /** Student's given name. Max 100 characters, must not be blank. */
    @NotBlank(message = "First name is required.")
    @Size(max = 100, message = "First name must not exceed 100 characters.")
    private String firstName;

    /** Student's family name. Max 100 characters, must not be blank. */
    @NotBlank(message = "Last name is required.")
    @Size(max = 100, message = "Last name must not exceed 100 characters.")
    private String lastName;

    /** Unique email address used as the login identifier. */
    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be a valid email address.")
    private String email;

    /**
     * Institutional registration / student number.
     * Must be unique across all users. Max 100 characters.
     */
    @NotBlank(message = "Registration number is required.")
    @Size(max = 100, message = "Registration number must not exceed 100 characters.")
    private String registrationNumber;

    /**
     * Plain-text password supplied by the user.
     * Validated by {@link com.supervisesuite.backend.common.validation.StrongPassword};
     * never persisted — hashed with BCrypt in the service layer.
     */
    @NotBlank(message = "Password is required.")
    @StrongPassword
    private String password;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
