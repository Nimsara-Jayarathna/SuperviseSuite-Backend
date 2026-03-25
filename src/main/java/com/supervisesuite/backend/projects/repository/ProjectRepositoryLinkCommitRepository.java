package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectRepositoryLinkCommit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepositoryLinkCommitRepository extends JpaRepository<ProjectRepositoryLinkCommit, UUID> {
    long countByProjectRepositoryLinkId(UUID projectRepositoryLinkId);

    Optional<ProjectRepositoryLinkCommit> findTopByProjectRepositoryLinkIdOrderByCommittedAtDesc(UUID projectRepositoryLinkId);

    Page<ProjectRepositoryLinkCommit> findByProjectRepositoryLinkIdOrderByCommittedAtDesc(
        UUID projectRepositoryLinkId,
        Pageable pageable
    );

    Optional<ProjectRepositoryLinkCommit> findByProjectRepositoryLinkIdAndSha(UUID projectRepositoryLinkId, String sha);

    List<ProjectRepositoryLinkCommit> findByProjectRepositoryLinkId(UUID projectRepositoryLinkId);

    void deleteByProjectRepositoryLinkId(UUID projectRepositoryLinkId);
    void deleteByRepositoryId(UUID repositoryId);
}
