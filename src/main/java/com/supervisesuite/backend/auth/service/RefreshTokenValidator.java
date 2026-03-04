package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.users.entity.User;

/**
 * Contract for validating an inbound raw refresh token and resolving the
 * associated user.
 *
 * <p>Decouples the refresh and logout endpoints from the details of how a
 * refresh token is looked up (hash comparison, expiry check, DB query).
 * Callers receive either a ready-to-use {@link User} or an exception —
 * they never handle token hashing or repository access directly.
 *
 * <p>SOLID rationale:
 * <ul>
 *   <li><b>SRP</b> — "validate a refresh token and return its owner" is a
 *       single, focused responsibility, separate from token issuance
 *       ({@link RefreshTokenService}) and access token validation
 *       ({@link com.supervisesuite.backend.auth.security.TokenService}).</li>
 *   <li><b>ISP</b> — one method; callers (the refresh endpoint) are never
 *       forced to depend on issuance or cookie-building logic.</li>
 *   <li><b>DIP</b> — the refresh endpoint controller depends on this
 *       interface, not on {@link com.supervisesuite.backend.auth.repository.RefreshTokenRepository}
 *       or any hashing implementation directly.</li>
 *   <li><b>OCP</b> — token storage strategy (DB hash, Redis, opaque store)
 *       can be changed by swapping the implementation without touching
 *       any controller.</li>
 * </ul>
 */
public interface RefreshTokenValidator {

    /**
     * Validates the given raw refresh token and returns the user it belongs to.
     *
     * <p>Validation steps the implementation must perform:
     * <ol>
     *   <li>Hash the raw token (SHA-256).</li>
     *   <li>Look up the hash in the token store — not found → throw.</li>
     *   <li>Check {@code expiresAt} against the current time — expired → throw.</li>
     *   <li>Return the associated {@link User}.</li>
     * </ol>
     *
     * <p>A single generic exception is thrown for all failure cases (not-found,
     * expired, malformed) to prevent token enumeration — callers cannot determine
     * which check failed.
     *
     * @param rawToken the raw (unhashed) refresh token received from the client cookie;
     *                 must not be {@code null}
     * @return the {@link User} associated with the valid, non-expired token
     * @throws com.supervisesuite.backend.common.error.UnauthorizedException if the token
     *         is unknown, expired, or otherwise invalid
     */
    User validate(String rawToken);
}
