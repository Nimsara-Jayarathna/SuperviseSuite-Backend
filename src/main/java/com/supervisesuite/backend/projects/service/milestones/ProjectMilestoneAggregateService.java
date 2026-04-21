package com.supervisesuite.backend.projects.service.milestones;

import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectMilestoneAggregateService {

    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final MilestonePolicyEngine milestonePolicyEngine;

    public ProjectMilestoneAggregateService(
            ProjectMilestoneRepository projectMilestoneRepository,
            MilestonePolicyEngine milestonePolicyEngine) {
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.milestonePolicyEngine = milestonePolicyEngine;
    }

    public ProjectMilestoneAggregates compute(List<ProjectMilestone> milestones) {
        int progressPercent = milestonePolicyEngine.calculateProgressPercent(milestones);
        LocalDate milestoneDate = milestonePolicyEngine.computeProjectMilestoneDate(milestones);
        return new ProjectMilestoneAggregates(progressPercent, milestoneDate);
    }

    public void applyTo(Project project, List<ProjectMilestone> milestones) {
        if (project == null) {
            return;
        }
        ProjectMilestoneAggregates aggregates = compute(milestones);
        project.setProgressPercent(aggregates.progressPercent());
        project.setMilestoneDate(aggregates.milestoneDate());
    }

    public List<ProjectMilestone> loadOrderedMilestones(UUID projectId) {
        if (projectId == null) {
            return List.of();
        }
        return projectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(projectId);
    }

    public record ProjectMilestoneAggregates(int progressPercent, LocalDate milestoneDate) {
    }
}

