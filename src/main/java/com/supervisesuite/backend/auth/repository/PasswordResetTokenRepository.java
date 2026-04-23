package com.supervisesuite.backend.auth.repository;

import com.supervisesuite.backend.auth.entity.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("""
        SELECT prt
        FROM PasswordResetToken prt
        JOIN FETCH prt.user
        WHERE prt.tokenHash = :tokenHash
          AND prt.usedAt IS NULL
          AND prt.expiresAt > :now
        """)
    Optional<PasswordResetToken> findActiveByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("""
        DELETE FROM PasswordResetToken prt
        WHERE prt.expiresAt < :now
           OR prt.usedAt IS NOT NULL
        """)
    int deleteExpiredOrUsed(@Param("now") Instant now);
}
