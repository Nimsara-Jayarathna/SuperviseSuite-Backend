package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ProjectProgressBackfillRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectProgressBackfillRunner.class);
    private static final String CANCELLED_MILESTONE_STATUS = "CANCELLED";
    private static final String COMPLETED_MILESTONE_STATUS = "COMPLETED";

    private final ProjectRepository projectRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;

    ProjectProgressBackfillRunner(
        ProjectRepository projectRepository,
        ProjectMilestoneRepository projectMilestoneRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Project> projects = projectRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        List<Project> projectsToUpdate = new ArrayList<>();

        for (Project project : projects) {
            List<ProjectMilestone> milestones = projectMilestoneRepository
                .findByProjectIdOrderBySequenceNoAsc(project.getId());
            int recalculatedProgress = calculateProgressPercent(milestones);

            if (!isSameProgress(project.getProgressPercent(), recalculatedProgress)) {
                project.setProgressPercent(recalculatedProgress);
                projectsToUpdate.add(project);
            }
        }

        if (!projectsToUpdate.isEmpty()) {
            projectRepository.saveAll(projectsToUpdate);
        }

        LOGGER.info(
            "Project progress backfill completed: scanned={}, updated={}",
            projects.size(),
            projectsToUpdate.size()
        );
    }

    private boolean isSameProgress(Integer currentProgress, int recalculatedProgress) {
        return currentProgress != null && currentProgress == recalculatedProgress;
    }

    private int calculateProgressPercent(List<ProjectMilestone> milestones) {
        long activeMilestones = milestones.stream()
            .filter(milestone -> !CANCELLED_MILESTONE_STATUS.equals(milestone.getStatus()))
            .count();

        if (activeMilestones == 0) {
            return 0;
        }

        long completedMilestones = milestones.stream()
            .filter(milestone -> !CANCELLED_MILESTONE_STATUS.equals(milestone.getStatus()))
            .filter(milestone -> COMPLETED_MILESTONE_STATUS.equals(milestone.getStatus()))
            .count();

        return (int) Math.round((completedMilestones * 100.0) / activeMilestones);
    }
}
