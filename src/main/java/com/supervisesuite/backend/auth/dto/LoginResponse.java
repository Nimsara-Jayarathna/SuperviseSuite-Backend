package com.supervisesuite.backend.auth.dto;

import java.util.UUID;

/**
 * Response payload returned by {@code POST /api/auth/login} on success.
 *
 * <p>Shape mirrors the frontend {@code AuthResponse} type exactly:
 * {@code accessToken}, {@code refreshToken}, and a nested {@code user} object.
 *
 * <p>Note: {@code isEmailVerified} is always {@code true} — email verification
 * is out of scope for this project (no verification flow exists).
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
     * {@code isEmailVerified} is always {@code true} — no email verification flow exists.
     */
    public static class UserInfo {

        private final UUID id;
        private final String email;
        private final String firstName;
        private final String lastName;
        private final String role;
        private final boolean isEmailVerified;

        public UserInfo(UUID id, String email, String firstName, String lastName, String role) {
            this.id = id;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.role = role;
            this.isEmailVerified = true;
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

        public boolean isEmailVerified() {
            return isEmailVerified;
        }
    }
}
