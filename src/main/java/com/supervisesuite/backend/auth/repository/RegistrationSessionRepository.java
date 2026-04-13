package com.supervisesuite.backend.auth.repository;

import com.supervisesuite.backend.auth.entity.RegistrationSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationSessionRepository extends JpaRepository<RegistrationSession, UUID> {

    Optional<RegistrationSession> findByTokenHash(String tokenHash);

    void deleteAllByExpiresAtBeforeOrUsedAtIsNotNull(Instant threshold);
}
