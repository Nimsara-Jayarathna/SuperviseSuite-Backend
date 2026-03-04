package com.supervisesuite.backend.supervisor.dto;

import java.util.UUID;

public class StudentSearchResultDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String registrationNumber;

    public StudentSearchResultDto() {
    }

    public StudentSearchResultDto(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String registrationNumber
    ) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.registrationNumber = registrationNumber;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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
}
