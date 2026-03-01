package com.supervisesuite.backend.auth.dto;

import java.util.UUID;

public class RegisterResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String registrationNumber;
    private String role;

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
