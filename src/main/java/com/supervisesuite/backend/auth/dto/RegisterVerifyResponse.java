package com.supervisesuite.backend.auth.dto;

public class RegisterVerifyResponse {

    private final String registrationToken;
    private final boolean requiresRoleSelection;
    private final String role;

    public RegisterVerifyResponse(
        String registrationToken,
        boolean requiresRoleSelection,
        String role
    ) {
        this.registrationToken = registrationToken;
        this.requiresRoleSelection = requiresRoleSelection;
        this.role = role;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public boolean isRequiresRoleSelection() {
        return requiresRoleSelection;
    }

    public String getRole() {
        return role;
    }
}
