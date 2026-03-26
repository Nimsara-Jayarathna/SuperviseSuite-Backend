package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.GitHubAccessRequestV2;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GitHubAccessRequestV2Repository extends JpaRepository<GitHubAccessRequestV2, UUID> {
    Optional<GitHubAccessRequestV2> findByTokenHash(String tokenHash);

    Optional<GitHubAccessRequestV2> findByResultTokenHash(String resultTokenHash);

    boolean existsByProjectIdAndUsedAtIsNotNullAndResultAcknowledgedAtIsNull(UUID projectId);

    Optional<GitHubAccessRequestV2> findFirstByProjectIdAndUsedAtIsNotNullAndResultAcknowledgedAtIsNullOrderByUsedAtDesc(UUID projectId);

    @Modifying
    @Query("""
        delete from GitHubAccessRequestV2 request
        where request.expiresAt <= :now
          and request.usedAt is not null
    """)
    int deleteExpiredUsed(@Param("now") Instant now);
}
