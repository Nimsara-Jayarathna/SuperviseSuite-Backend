package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepositoryCacheRepository extends JpaRepository<ProjectRepository, UUID> {
    Optional<ProjectRepository> findByProjectIdAndIsPrimaryTrue(UUID projectId);

    List<ProjectRepository> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
}
