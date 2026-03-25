package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.GitHubRepositoryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubRepositoryEntityRepository extends JpaRepository<GitHubRepositoryEntity, UUID> {
    List<GitHubRepositoryEntity> findByAccessSourceIdOrderByFullNameAsc(UUID accessSourceId);

    Optional<GitHubRepositoryEntity> findByAccessSourceIdAndGithubRepoId(UUID accessSourceId, Long githubRepoId);
}
