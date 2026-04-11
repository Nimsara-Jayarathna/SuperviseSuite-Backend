package com.supervisesuite.backend.auth.dto;

public class RegisterVerifyResponse {

    private final String registrationToken;

    public RegisterVerifyResponse(String registrationToken) {
        this.registrationToken = registrationToken;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }
}
