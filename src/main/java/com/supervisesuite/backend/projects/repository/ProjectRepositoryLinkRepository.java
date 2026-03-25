package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepositoryLinkRepository extends JpaRepository<ProjectRepositoryLink, UUID> {
    List<ProjectRepositoryLink> findByProjectIdOrderByLinkedAtDesc(UUID projectId);

    Optional<ProjectRepositoryLink> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<ProjectRepositoryLink> findByProjectIdAndIsPrimaryTrue(UUID projectId);

    boolean existsByProjectIdAndGithubRepoId(UUID projectId, Long githubRepoId);

    long countByProjectId(UUID projectId);
}
