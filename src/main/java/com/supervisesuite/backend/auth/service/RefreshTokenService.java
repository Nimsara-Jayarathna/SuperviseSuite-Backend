package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.users.entity.User;

/**
 * Contract for refresh token operations.
 *
 * <p>Separating refresh token lifecycle management from the main
 * {@link AuthService} satisfies the Single Responsibility Principle —
 * {@link AuthService} owns the authentication flow while this service
 * owns secure token generation and storage.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Generate tokens with sufficient entropy (≥ 32 random bytes).</li>
 *   <li>Never persist the raw token — only a secure hash (e.g. SHA-256).</li>
 *   <li>Return the raw token once so the caller can hand it to the client.</li>
 * </ul>
 */
public interface RefreshTokenService {

    /**
     * Generates a cryptographically secure refresh token for the given user,
     * persists its hash, and returns the raw token string.
     *
     * <p>The raw token is never stored — only its SHA-256 hash is kept in the DB.
     * The caller must return it to the client exactly once and never log it.
     *
     * @param user the authenticated user to associate the token with
     * @return the raw (unhashed) refresh token string
     */
    String issue(User user);

    /**
     * Marks the refresh token identified by the given raw token as revoked.
     *
     * <p>Sets {@code revokedAt} to the current instant so that subsequent
     * validation attempts are rejected immediately. Used during token rotation
     * (refresh flow) and logout.
     *
     * <p>If the token hash is not found in the store the call is silently ignored —
     * it is safe to call this method on already-deleted or unknown tokens.
     *
     * @param rawToken the raw (unhashed) refresh token received from the client
     */
    void revoke(String rawToken);
}
