package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.GitHubAccessSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubAccessSourceRepository extends JpaRepository<GitHubAccessSource, UUID> {
    List<GitHubAccessSource> findByProjectIdAndIsActiveTrueOrderByCreatedAtDesc(UUID projectId);

    Optional<GitHubAccessSource> findByIdAndProjectIdAndIsActiveTrue(UUID id, UUID projectId);

    Optional<GitHubAccessSource> findByIdAndIsActiveTrue(UUID id);

    Optional<GitHubAccessSource> findByProjectIdAndInstallationIdAndIsActiveTrue(UUID projectId, Long installationId);
}
