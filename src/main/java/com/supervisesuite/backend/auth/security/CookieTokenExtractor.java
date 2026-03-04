package com.supervisesuite.backend.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Contract for extracting a raw access token string from an incoming HTTP request.
 *
 * <p>Decouples {@link JwtAuthFilter} from the mechanism used to carry the token
 * (cookie, {@code Authorization} header, query parameter, etc.). The filter asks
 * "give me the token from this request" — it does not know or care where the
 * token lives.
 *
 * <p>SOLID rationale:
 * <ul>
 *   <li><b>SRP</b> — "locate the token in the request" is its own responsibility,
 *       separate from "validate the token" ({@link TokenService}) and
 *       "set up the security context" ({@link JwtAuthFilter}).</li>
 *   <li><b>ISP</b> — a single focused method; callers are never forced to
 *       depend on extraction logic they do not use.</li>
 *   <li><b>DIP</b> — {@link JwtAuthFilter} depends on this interface, not on
 *       {@code HttpServletRequest#getCookies()} or header-reading code directly.</li>
 *   <li><b>OCP</b> — switching from cookie-based extraction back to
 *       {@code Authorization: Bearer} (or supporting both) requires only a new
 *       implementation; the filter is never touched.</li>
 * </ul>
 */
public interface CookieTokenExtractor {

    /**
     * Attempts to extract a raw access token string from the given request.
     *
     * <p>Returns {@link Optional#empty()} when the token is absent — the caller
     * treats an empty result as an unauthenticated request and continues the
     * filter chain without setting a {@code SecurityContext}. No exception is
     * thrown for a missing token, only for genuinely unexpected conditions.
     *
     * @param request the current HTTP servlet request; never {@code null}
     * @return an {@link Optional} containing the raw token string if present,
     *         or {@link Optional#empty()} if the request carries no recognisable token
     */
    Optional<String> extractAccessToken(HttpServletRequest request);
}
