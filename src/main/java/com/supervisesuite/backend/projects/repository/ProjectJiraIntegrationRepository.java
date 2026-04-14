package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectJiraIntegrationRepository extends JpaRepository<ProjectJiraIntegration, UUID> {

    Optional<ProjectJiraIntegration> findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(UUID projectId);

    List<ProjectJiraIntegration> findAllByProjectIdInAndRevokedAtIsNull(List<UUID> projectIds);

    org.springframework.data.domain.Page<ProjectJiraIntegration> findByRevokedAtIsNullOrderByConnectedAtAsc(org.springframework.data.domain.Pageable pageable);
}
