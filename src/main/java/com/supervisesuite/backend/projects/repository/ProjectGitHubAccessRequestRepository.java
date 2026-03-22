package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectGitHubAccessRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface ProjectGitHubAccessRequestRepository extends JpaRepository<ProjectGitHubAccessRequest, UUID> {

    Optional<ProjectGitHubAccessRequest> findByTokenHash(String tokenHash);

    Optional<ProjectGitHubAccessRequest> findByGithubStateHash(String githubStateHash);

    Optional<ProjectGitHubAccessRequest> findByResultTokenHash(String resultTokenHash);

    @Modifying
    @Query("""
        delete from ProjectGitHubAccessRequest request
        where request.status in :statuses
          and request.expiresAt <= :now
    """)
    int deleteExpiredRequestsByStatuses(
        @Param("statuses") Collection<String> statuses,
        @Param("now") Instant now
    );

    @Modifying
    @Query("""
        update ProjectGitHubAccessRequest request
        set request.resultTokenHash = null,
            request.resultExpiresAt = null,
            request.updatedAt = :now
        where request.resultTokenHash is not null
          and request.resultExpiresAt is not null
          and request.resultExpiresAt <= :now
    """)
    int clearExpiredResultTokens(@Param("now") Instant now);
}
