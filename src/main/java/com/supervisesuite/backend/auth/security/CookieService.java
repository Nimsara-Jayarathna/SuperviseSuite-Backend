package com.supervisesuite.backend.auth.security;

import org.springframework.http.ResponseCookie;

/**
 * Contract for building auth-related HTTP cookies.
 *
 * <p>Centralises all cookie construction rules — name, path, flags ({@code HttpOnly},
 * {@code Secure}, {@code SameSite}), and max-age — so that every component that
 * needs to attach or clear a cookie depends on this interface rather than on
 * Spring's {@link ResponseCookie} builder directly.
 *
 * <p>SOLID rationale:
 * <ul>
 *   <li><b>SRP</b> — cookie attribute decisions (name, path, TTL, security flags)
 *       live in exactly one place; callers only ask for a ready-made cookie object.</li>
 *   <li><b>DIP</b> — {@link com.supervisesuite.backend.auth.controller.AuthController}
 *       and any future logout or refresh endpoint depend on this abstraction, not on
 *       the concrete builder used by the implementation.</li>
 *   <li><b>OCP</b> — switching cookie strategy (e.g. adding a {@code __Host-} prefix,
 *       or changing {@code SameSite} policy) requires only a new or updated
 *       implementation, not changes in every controller.</li>
 * </ul>
 *
 * <p>Cookie names are declared as constants on this interface so that both the
 * implementation and any test or extractor that needs to reference a cookie by name
 * share a single source of truth.
 */
public interface CookieService {

    /** Name of the httpOnly cookie that carries the short-lived JWT access token. */
    String ACCESS_TOKEN_COOKIE = "ss_access_token";

    /** Name of the httpOnly cookie that carries the long-lived refresh token. */
    String REFRESH_TOKEN_COOKIE = "ss_refresh_token";

    /**
     * Builds a cookie that carries a new access token.
     *
     * <p>The returned cookie must be {@code HttpOnly}, {@code Secure},
     * {@code SameSite=Strict}, scoped to {@code Path=/api}, and expire after
     * the configured access-token TTL.
     *
     * @param token the signed JWT access token string
     * @return a fully configured {@link ResponseCookie} ready to be added to the response
     */
    ResponseCookie buildAccessTokenCookie(String token);

    /**
     * Builds a cookie that carries a new refresh token.
     *
     * <p>The returned cookie must be {@code HttpOnly}, {@code Secure},
     * {@code SameSite=Strict}, scoped to {@code Path=/api/auth}, and expire after
     * the configured refresh-token TTL.
     *
     * <p>The refresh token path is intentionally narrower than the access token —
     * the browser only sends it to {@code /api/auth/*} endpoints, reducing its
     * exposure surface.
     *
     * @param token the raw (unhashed) refresh token string
     * @return a fully configured {@link ResponseCookie} ready to be added to the response
     */
    ResponseCookie buildRefreshTokenCookie(String token);

    /**
     * Builds an expired placeholder cookie that instructs the browser to delete
     * the access token cookie immediately ({@code Max-Age=0}).
     *
     * @return a zero-max-age {@link ResponseCookie} for {@value #ACCESS_TOKEN_COOKIE}
     */
    ResponseCookie buildClearAccessTokenCookie();

    /**
     * Builds an expired placeholder cookie that instructs the browser to delete
     * the refresh token cookie immediately ({@code Max-Age=0}).
     *
     * @return a zero-max-age {@link ResponseCookie} for {@value #REFRESH_TOKEN_COOKIE}
     */
    ResponseCookie buildClearRefreshTokenCookie();
}
