package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.milestones.ProjectMilestoneAggregateService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name = "app.milestones.backfill-on-startup",
    havingValue = "true",
    matchIfMissing = true
)
class ProjectProgressBackfillRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectProgressBackfillRunner.class);

    private final ProjectRepository projectRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final ProjectMilestoneAggregateService projectMilestoneAggregateService;

    ProjectProgressBackfillRunner(
        ProjectRepository projectRepository,
        ProjectMilestoneRepository projectMilestoneRepository,
        ProjectMilestoneAggregateService projectMilestoneAggregateService
    ) {
        this.projectRepository = projectRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.projectMilestoneAggregateService = projectMilestoneAggregateService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Project> projects = projectRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        List<Project> projectsToUpdate = new ArrayList<>();

        for (Project project : projects) {
            List<ProjectMilestone> milestones = projectMilestoneRepository
                .findByProjectIdOrderBySequenceNoAsc(project.getId());
            ProjectMilestoneAggregateService.ProjectMilestoneAggregates aggregates =
                projectMilestoneAggregateService.compute(milestones);

            boolean progressChanged = !isSameProgress(project.getProgressPercent(), aggregates.progressPercent());
            boolean milestoneDateChanged = !java.util.Objects.equals(project.getMilestoneDate(), aggregates.milestoneDate());

            if (progressChanged || milestoneDateChanged) {
                project.setProgressPercent(aggregates.progressPercent());
                project.setMilestoneDate(aggregates.milestoneDate());
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
}
