package com.supervisesuite.backend.auth.dto;

import java.util.UUID;

/**
 * Internal DTO returned by {@link com.supervisesuite.backend.auth.service.AuthService}.
 *
 * <p>Carries the raw access token, refresh token, and user profile from the
 * service layer to the controller. The controller places the tokens in httpOnly
 * cookies and sends only the {@link UserInfo} in the JSON response body via
 * {@link LoginUserResponse}.
 */
public class LoginResponse {

    private final String accessToken;
    private final String refreshToken;
    private final UserInfo user;

    public LoginResponse(String accessToken, String refreshToken, UserInfo user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public UserInfo getUser() {
        return user;
    }

    /**
     * Public profile of the authenticated user embedded in the login response.
     *
     * <p>Intentionally excludes sensitive fields such as {@code passwordHash}.
     */
    public static class UserInfo {

        private final UUID id;
        private final String email;
        private final String firstName;
        private final String lastName;
        private final String role;

        public UserInfo(UUID id, String email, String firstName, String lastName, String role) {
            this.id = id;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
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

        public String getRole() {
            return role;
        }
    }
}
