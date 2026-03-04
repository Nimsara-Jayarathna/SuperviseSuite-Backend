package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, UUID> {
}
