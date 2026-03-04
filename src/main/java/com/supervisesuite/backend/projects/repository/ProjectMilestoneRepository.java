package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, UUID> {
    List<ProjectMilestone> findByProjectIdOrderBySequenceNoAsc(UUID projectId);
}
