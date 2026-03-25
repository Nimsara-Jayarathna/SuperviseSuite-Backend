package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepositoryLinkRepository extends JpaRepository<ProjectRepositoryLink, UUID> {
    Optional<ProjectRepositoryLink> findByProjectIdAndGithubRepositoryId(UUID projectId, UUID githubRepositoryId);

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

    void deleteByProjectId(UUID projectId);

    void deleteByGithubInstallationId(Long githubInstallationId);
}
