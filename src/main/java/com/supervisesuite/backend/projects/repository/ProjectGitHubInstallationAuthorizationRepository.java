package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectGitHubInstallationAuthorization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectGitHubInstallationAuthorizationRepository
    extends JpaRepository<ProjectGitHubInstallationAuthorization, UUID> {

    Optional<ProjectGitHubInstallationAuthorization> findByProjectIdAndInstallationId(UUID projectId, Long installationId);

    Optional<ProjectGitHubInstallationAuthorization> findTopByProjectIdOrderByAuthorizedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);

    void deleteByInstallationId(Long installationId);
}
