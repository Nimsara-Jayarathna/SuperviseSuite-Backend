package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.GitHubRepositoryEntity;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubRepositoryEntityRepository extends JpaRepository<GitHubRepositoryEntity, UUID> {
    List<GitHubRepositoryEntity> findByAccessSourceIdOrderByFullNameAsc(UUID accessSourceId);

    Optional<GitHubRepositoryEntity> findByAccessSourceIdAndGithubRepoId(UUID accessSourceId, Long githubRepoId);

    Optional<GitHubRepositoryEntity> findByAccessSourceIdAndHtmlUrl(UUID accessSourceId, String htmlUrl);

    @org.springframework.data.jpa.repository.Query("""
        select r from GitHubRepositoryEntity r
        join GitHubAccessSource s on r.accessSourceId = s.id
        where s.installationId = :installationId
    """)
    List<GitHubRepositoryEntity> findByAccessSourceInstallationId(@org.springframework.data.repository.query.Param("installationId") Long installationId);
}
