package com.supervisesuite.backend.auth.repository;

import com.supervisesuite.backend.auth.entity.RefreshToken;
import com.supervisesuite.backend.users.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Used on logout to revoke all active tokens for a user. */
    void deleteAllByUser(User user);
}
