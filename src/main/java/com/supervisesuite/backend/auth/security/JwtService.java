package com.supervisesuite.backend.auth.security;

import com.supervisesuite.backend.config.JwtProperties;
import com.supervisesuite.backend.users.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService implements TokenService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
    }

    /**
     * Generates a signed HS256 access token for the given user.
     *
     * <p>Claims embedded:
     * <ul>
     *   <li>{@code sub} — the user's UUID as a string</li>
     *   <li>{@code role} — the user's role (e.g. {@code STUDENT} or {@code SUPERVISOR})</li>
     *   <li>{@code iat} — issued-at timestamp</li>
     *   <li>{@code exp} — expiry from {@link JwtProperties#getAccessTokenExpirySeconds()}</li>
     * </ul>
     *
     * @param user the authenticated user
     * @return a compact, signed JWT string
     */
    public String generateAccessToken(User user) {
        long nowMillis = System.currentTimeMillis();
        Date issuedAt = new Date(nowMillis);
        Date expiration = new Date(nowMillis + jwtProperties.getAccessTokenExpirySeconds() * 1000L);

        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("role", user.getRole())
            .issuedAt(issuedAt)
            .expiration(expiration)
            .signWith(signingKey)
            .compact();
    }

    /**
     * Extracts the {@code sub} (subject / userId) claim from the given JWT.
     *
     * @param token a compact JWT string
     * @return an {@link Optional} containing the subject if the token is valid;
     *         {@code Optional.empty()} if the token is expired, malformed, or has an invalid signature
     */
    public Optional<String> extractSubject(String token) {
        return extractAllClaims(token).map(Claims::getSubject);
    }

    /**
     * Extracts the custom {@code role} claim from the given JWT.
     *
     * @param token a compact JWT string
     * @return an {@link Optional} containing the role string if the token is valid;
     *         {@code Optional.empty()} if the token is invalid or the claim is absent
     */
    public Optional<String> extractRole(String token) {
        return extractAllClaims(token).map(claims -> claims.get("role", String.class));
    }

    /**
     * Parses and verifies the JWT signature, returning all claims on success.
     *
     * <p>All public extraction methods delegate here so exception handling is
     * centralised in one place. Any {@link JwtException} (expired, malformed,
     * wrong signature) produces {@code Optional.empty()} rather than propagating.
     *
     * @param token a compact JWT string
     * @return an {@link Optional} containing the {@link Claims} payload, or empty on any failure
     */
    private Optional<Claims> extractAllClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
