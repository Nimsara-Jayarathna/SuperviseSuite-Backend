package com.supervisesuite.backend.auth.repository;

import com.supervisesuite.backend.auth.entity.VerificationToken;
import com.supervisesuite.backend.users.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByToken(String token);

    /** Used to invalidate existing unused tokens when resending verification email. */
    void deleteAllByUserAndUsedAtIsNull(User user);
}
