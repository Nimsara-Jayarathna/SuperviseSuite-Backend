package com.supervisesuite.backend.projectfiles.repository;

import com.supervisesuite.backend.projectfiles.entity.ProjectFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, UUID> {
    List<ProjectFile> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID projectId);

    Optional<ProjectFile> findByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);
}
