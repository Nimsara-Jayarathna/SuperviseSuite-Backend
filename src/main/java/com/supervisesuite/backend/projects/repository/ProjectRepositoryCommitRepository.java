package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectRepositoryCommit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepositoryCommitRepository extends JpaRepository<ProjectRepositoryCommit, UUID> {
    Optional<ProjectRepositoryCommit> findByRepositoryIdAndSha(UUID repositoryId, String sha);

    long countByRepositoryId(UUID repositoryId);

    Optional<ProjectRepositoryCommit> findTopByRepositoryIdOrderByCommittedAtDesc(UUID repositoryId);

    List<ProjectRepositoryCommit> findTop10ByRepositoryIdOrderByCommittedAtDesc(UUID repositoryId);

    Page<ProjectRepositoryCommit> findByRepositoryIdOrderByCommittedAtDesc(UUID repositoryId, Pageable pageable);

    void deleteByRepositoryId(UUID repositoryId);
}
