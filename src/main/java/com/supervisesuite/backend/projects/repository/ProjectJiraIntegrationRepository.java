package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectJiraIntegrationRepository extends JpaRepository<ProjectJiraIntegration, UUID> {

    Optional<ProjectJiraIntegration> findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(UUID projectId);

    List<ProjectJiraIntegration> findAllByProjectIdInAndRevokedAtIsNull(List<UUID> projectIds);

    org.springframework.data.domain.Page<ProjectJiraIntegration> findByRevokedAtIsNullOrderByConnectedAtAsc(org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("""
        update ProjectJiraIntegration integration
           set integration.syncStatus = :inProgress,
               integration.lastSyncAttemptedAt = :attemptedAt,
               integration.updatedAt = :updatedAt,
               integration.syncError = null
         where integration.projectId = :projectId
           and integration.revokedAt is null
           and (
               integration.syncStatus is null
               or integration.syncStatus <> :inProgress
               or integration.lastSyncAttemptedAt is null
               or integration.lastSyncAttemptedAt < :staleBefore
           )
    """)
    int claimForSync(
        @Param("projectId") UUID projectId,
        @Param("inProgress") String inProgress,
        @Param("attemptedAt") Instant attemptedAt,
        @Param("updatedAt") Instant updatedAt,
        @Param("staleBefore") Instant staleBefore
    );
}
