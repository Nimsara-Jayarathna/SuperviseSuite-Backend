package com.supervisesuite.backend.auth.repository;

import com.supervisesuite.backend.auth.entity.RefreshToken;
import com.supervisesuite.backend.users.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Like {@link #findByTokenHash} but JOIN FETCHes the associated {@link User}
     * in the same query, preventing lazy-initialization issues when the token
     * is used outside an active JPA session.
     */
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashWithUser(@Param("hash") String hash);

    /** Used on logout to revoke all active tokens for a user. */
    void deleteAllByUser(User user);

    void deleteAllByUserId(UUID userId);
}
