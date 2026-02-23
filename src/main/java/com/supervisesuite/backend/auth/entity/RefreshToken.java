package com.supervisesuite.backend.auth.entity;

import com.supervisesuite.backend.users.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * SHA-256 hash of the raw refresh token.
     * The raw token is never stored — only ever held in memory and sent to the client once.
     */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * Set when the token is revoked (logout or rotation). Null means still active.
     */
    private Instant revokedAt;

    @Column(nullable = false)
    private Instant createdAt;
}
