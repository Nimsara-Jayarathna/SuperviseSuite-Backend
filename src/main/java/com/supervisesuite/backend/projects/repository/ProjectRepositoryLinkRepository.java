package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepositoryLinkRepository extends JpaRepository<ProjectRepositoryLink, UUID> {
    Optional<ProjectRepositoryLink> findByProjectIdAndGithubRepositoryId(UUID projectId, UUID githubRepositoryId);
    Optional<ProjectRepositoryLink> findByProjectIdAndGithubRepoId(UUID projectId, Long githubRepoId);

    List<ProjectRepositoryLink> findByProjectIdOrderByLinkedAtDesc(UUID projectId);

    List<ProjectRepositoryLink> findByProjectIdAndIsEnabledTrueOrderByLinkedAtDesc(UUID projectId);

    Optional<ProjectRepositoryLink> findByProjectIdAndRepositoryUrl(UUID projectId, String repositoryUrl);


    List<ProjectRepositoryLink> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    List<ProjectRepositoryLink> findByGithubInstallationId(Long githubInstallationId);

    Optional<ProjectRepositoryLink> findTopByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<ProjectRepositoryLink> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<ProjectRepositoryLink> findByProjectIdAndIsPrimaryTrue(UUID projectId);

    Optional<ProjectRepositoryLink> findByProjectIdAndIsPrimaryTrueAndIsEnabledTrue(UUID projectId);

    boolean existsByProjectIdAndGithubRepoId(UUID projectId, Long githubRepoId);

    boolean existsByProjectIdAndGithubRepositoryIdIn(UUID projectId, Collection<UUID> githubRepositoryIds);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndIsEnabledTrue(UUID projectId);

    void deleteByProjectIdAndGithubRepositoryIdIn(UUID projectId, Collection<UUID> githubRepositoryIds);

    List<ProjectRepositoryLink> findByIsEnabledTrueOrderByLastSyncedAtAsc(org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("""
        update ProjectRepositoryLink link
           set link.syncStatus = :inProgress,
               link.lastSyncAttemptedAt = :attemptedAt,
               link.updatedAt = :updatedAt
         where link.id = :linkId
           and link.isEnabled = true
           and (
               link.syncStatus is null
               or link.syncStatus <> :inProgress
               or link.lastSyncAttemptedAt is null
               or link.lastSyncAttemptedAt < :staleBefore
           )
    """)
    int claimForSync(
        @Param("linkId") UUID linkId,
        @Param("inProgress") String inProgress,
        @Param("attemptedAt") Instant attemptedAt,
        @Param("updatedAt") Instant updatedAt,
        @Param("staleBefore") Instant staleBefore
    );

    void deleteByProjectId(UUID projectId);

    void deleteByGithubInstallationId(Long githubInstallationId);

    @Query(
        value = "select id from project_repository_links where id = :linkId for update nowait",
        nativeQuery = true
    )
    List<UUID> lockByIdNowait(@Param("linkId") UUID linkId);
}
