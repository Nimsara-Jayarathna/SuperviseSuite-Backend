package com.supervisesuite.backend.auth.dto;

import java.util.UUID;

/**
 * Response payload returned by {@code POST /api/auth/register} on success.
 *
 * <p>Contains the newly created student's public profile fields.
 * Sensitive fields such as {@code passwordHash} are intentionally excluded.
 */
public class RegisterResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String registrationNumber;
    private String role;

    /**
     * Constructs a registration response from the saved {@link com.supervisesuite.backend.users.entity.User}.
     *
     * @param id                 the generated UUID of the new user
     * @param email              the user's email address
     * @param firstName          the user's given name
     * @param lastName           the user's family name
     * @param registrationNumber the user's institutional registration number
     * @param role               the assigned role (always {@code STUDENT} for this endpoint)
     */
    public RegisterResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String registrationNumber,
        String role
    ) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.registrationNumber = registrationNumber;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getRole() {
        return role;
    }
}
