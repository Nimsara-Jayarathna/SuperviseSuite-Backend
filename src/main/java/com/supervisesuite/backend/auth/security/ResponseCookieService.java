package com.supervisesuite.backend.auth.security;

import com.supervisesuite.backend.config.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link CookieService} using Spring's
 * {@link ResponseCookie} builder.
 *
 * <p>All cookie attribute decisions — name, path, TTL, and security flags —
 * are centralised here. No other class constructs auth cookies directly.
 *
 * <p>Cookie attributes applied to every issued cookie:
 * <ul>
 *   <li>{@code HttpOnly} — the cookie is invisible to JavaScript, eliminating
 *       direct token theft via XSS.</li>
 *   <li>{@code Secure} — the browser only transmits the cookie over HTTPS.</li>
 *   <li>{@code SameSite=Strict} — the browser refuses to attach the cookie on
 *       any cross-site request, defeating CSRF without a separate token.</li>
 * </ul>
 *
 * <p>Path scoping:
 * <ul>
 *   <li>Access token → {@code Path=/api} — sent on all API calls.</li>
 *   <li>Refresh token → {@code Path=/api/auth} — sent only to auth endpoints,
 *       reducing the refresh token's exposure surface.</li>
 * </ul>
 *
 * <p>Package-private — callers depend on {@link CookieService} only.
 */
@Component
class ResponseCookieService implements CookieService {

    private final JwtProperties jwtProperties;
    private final boolean cookiesSecure;

    ResponseCookieService(
        JwtProperties jwtProperties,
        @Value("${app.cookie.secure:true}") boolean cookiesSecure
    ) {
        this.jwtProperties = jwtProperties;
        this.cookiesSecure = cookiesSecure;
    }

    @Override
    public ResponseCookie buildAccessTokenCookie(String token) {
        return base(ACCESS_TOKEN_COOKIE, token)
            .path("/api")
            .maxAge(jwtProperties.getAccessTokenExpirySeconds())
            .build();
    }

    @Override
    public ResponseCookie buildRefreshTokenCookie(String token) {
        return base(REFRESH_TOKEN_COOKIE, token)
            .path("/api/auth")
            .maxAge(jwtProperties.getRefreshTokenExpirySeconds())
            .build();
    }

    @Override
    public ResponseCookie buildClearAccessTokenCookie() {
        return base(ACCESS_TOKEN_COOKIE, "")
            .path("/api")
            .maxAge(0)
            .build();
    }

    @Override
    public ResponseCookie buildClearRefreshTokenCookie() {
        return base(REFRESH_TOKEN_COOKIE, "")
            .path("/api/auth")
            .maxAge(0)
            .build();
    }

    /**
     * Shared builder pre-configured with the security flags common to all auth cookies.
     *
     * <p>Individual methods set path and max-age on top of this base.
     */
    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookiesSecure)
            .sameSite("Strict");
    }
}
