package com.supervisesuite.backend.auth.repository;

import com.supervisesuite.backend.auth.entity.EmailOtp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {

    Optional<EmailOtp> findTopByEmailAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
        String email, Instant now
    );

    void deleteAllByExpiresAtBeforeOrUsedAtIsNotNull(Instant threshold);
}
