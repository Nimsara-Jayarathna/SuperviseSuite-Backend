package com.supervisesuite.backend.auth.dto;

/**
 * Response body returned by {@code POST /api/auth/login} after the migration to
 * httpOnly cookies.
 *
 * <p>Access and refresh tokens are no longer included in the JSON body — they are
 * delivered as {@code HttpOnly; Secure; SameSite=Strict} cookies by the controller.
 * The body carries only the user's public profile so the frontend can populate its
 * auth state without an extra round-trip.
 */
public class LoginUserResponse {

    private final LoginResponse.UserInfo user;

    public LoginUserResponse(LoginResponse.UserInfo user) {
        this.user = user;
    }

    public LoginResponse.UserInfo getUser() {
        return user;
    }
}
