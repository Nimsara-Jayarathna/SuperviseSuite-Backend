package com.supervisesuite.backend.auth.security;

import com.supervisesuite.backend.users.entity.User;
import java.util.Optional;

/**
 * Contract for JWT token operations.
 *
 * <p>Abstracts access token generation and claim extraction so that higher-level
 * components ({@link com.supervisesuite.backend.auth.service.AuthService},
 * {@link JwtAuthFilter}) depend on this interface rather than on the concrete
 * {@link JwtService} — satisfying the Dependency Inversion Principle.
 *
 * <p>All extraction methods return {@link Optional} so callers never receive raw
 * exceptions from the JWT library; an empty result signals any validation failure
 * (expired, malformed, wrong signature).
 */
public interface TokenService {

    /**
     * Generates a signed, short-lived access token for the given user.
     *
     * @param user the authenticated user whose identity and role to embed
     * @return a compact, signed JWT string
     */
    String generateAccessToken(User user);

    /**
     * Extracts the {@code sub} (subject / userId) claim from a token.
     *
     * @param token a compact JWT string
     * @return the subject if the token is valid; {@link Optional#empty()} otherwise
     */
    Optional<String> extractSubject(String token);

    /**
     * Extracts the custom {@code role} claim from a token.
     *
     * @param token a compact JWT string
     * @return the role string if the token is valid; {@link Optional#empty()} otherwise
     */
    Optional<String> extractRole(String token);
}
