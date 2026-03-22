package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.GitHubAppInstallation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubAppInstallationRepository extends JpaRepository<GitHubAppInstallation, Long> {
    Optional<GitHubAppInstallation> findByInstallationId(Long installationId);
}

