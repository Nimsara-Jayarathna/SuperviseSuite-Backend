package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectGitHubAccessRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectGitHubAccessRequestRepository extends JpaRepository<ProjectGitHubAccessRequest, UUID> {

    Optional<ProjectGitHubAccessRequest> findByTokenHash(String tokenHash);

    Optional<ProjectGitHubAccessRequest> findByGithubStateHash(String githubStateHash);
}
