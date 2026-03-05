package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.Project;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findBySupervisorIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID supervisorId);

    Optional<Project> findByIdAndSupervisor_IdAndDeletedAtIsNull(UUID id, UUID supervisorId);

    Optional<Project> findByIdAndDeletedAtIsNull(UUID id);

    List<Project> findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(Collection<UUID> ids);
}
