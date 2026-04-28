package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.common.util.EntityIdParser;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.milestones.MilestonePolicyEngine;
import com.supervisesuite.backend.projects.service.milestones.ProjectMilestoneAggregateService;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.users.entity.User;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupervisorProjectMilestoneService {

    private static final String DEFAULT_MILESTONE_STATUS = "PLANNED";

    private final ProjectRepository projectRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final MilestonePolicyEngine milestonePolicyEngine;
    private final ProjectMilestoneAggregateService projectMilestoneAggregateService;
    private final ProjectAccessGuard projectAccessGuard;
    private final SupervisorProjectDtoMapper projectDtoMapper;

    SupervisorProjectMilestoneService(
            ProjectRepository projectRepository,
            ProjectMilestoneRepository projectMilestoneRepository,
            MilestonePolicyEngine milestonePolicyEngine,
            ProjectMilestoneAggregateService projectMilestoneAggregateService,
            ProjectAccessGuard projectAccessGuard,
            SupervisorProjectDtoMapper projectDtoMapper) {
        this.projectRepository = projectRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.milestonePolicyEngine = milestonePolicyEngine;
        this.projectMilestoneAggregateService = projectMilestoneAggregateService;
        this.projectAccessGuard = projectAccessGuard;
        this.projectDtoMapper = projectDtoMapper;
    }

    @Transactional
    SupervisorProjectDetailDto addProjectMilestone(
            User supervisor,
            String projectId,
            AddSupervisorProjectMilestoneRequest request) {
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parseProjectId(projectId));

        List<ProjectMilestone> existingMilestones = projectMilestoneRepository
                .findByProjectIdOrderBySequenceNoAsc(project.getId());
        Integer nextSequenceNo = existingMilestones.isEmpty()
                ? 1
                : existingMilestones.get(existingMilestones.size() - 1).getSequenceNo() + 1;

        LocalDate dueDate = request.getDueDate();
        LocalDate today = LocalDate.now();
        milestonePolicyEngine.validateDueDateForStatus(dueDate, DEFAULT_MILESTONE_STATUS, today);
        LocalDate previousDueDate = existingMilestones.isEmpty()
                ? null
                : existingMilestones.get(existingMilestones.size() - 1).getDueDate();
        milestonePolicyEngine.validateChronologyWithPrevious(previousDueDate, dueDate);

        Instant now = Instant.now();
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setProjectId(project.getId());
        milestone.setTitle(request.getTitle().trim());
        milestone.setDescription(NormalizationUtils.trimToNull(request.getDescription()));
        milestone.setDueDate(dueDate);
        milestone.setStatus(DEFAULT_MILESTONE_STATUS);
        milestone.setSequenceNo(nextSequenceNo);
        milestone.setCreatedBy(supervisor.getId());
        milestone.setCreatedAt(now);
        projectMilestoneRepository.save(milestone);

        List<ProjectMilestone> milestonesForAggregates = new ArrayList<>(existingMilestones.size() + 1);
        milestonesForAggregates.addAll(existingMilestones);
        milestonesForAggregates.add(milestone);
        projectMilestoneAggregateService.applyTo(project, milestonesForAggregates);
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        projectRepository.save(project);

        return projectDtoMapper.toProjectDetail(project);
    }

    @Transactional
    SupervisorProjectDetailDto updateProjectMilestone(
            User supervisor,
            String projectId,
            String milestoneId,
            UpdateSupervisorProjectMilestoneRequest request) {
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parseProjectId(projectId));
        UUID parsedMilestoneId = parseMilestoneId(milestoneId);

        ProjectMilestone milestone = projectMilestoneRepository.findByIdAndProjectId(parsedMilestoneId, project.getId())
                .orElseThrow(EntityNotFoundException::new);

        String milestoneStatus = milestonePolicyEngine.normalizeAndValidateStatus(request.getStatus());
        LocalDate requestedDueDate = request.getDueDate();
        String currentStatus = milestone.getStatus();
        LocalDate currentDueDate = milestone.getDueDate();
        boolean statusChanged = !Objects.equals(currentStatus, milestoneStatus);
        boolean dueDateChanged = !Objects.equals(currentDueDate, requestedDueDate);

        if (statusChanged) {
            milestonePolicyEngine.validateStatusTransition(currentStatus, milestoneStatus);
        }
        if (statusChanged || dueDateChanged) {
            milestonePolicyEngine.validateDueDateForStatus(requestedDueDate, milestoneStatus, LocalDate.now());
        }

        List<ProjectMilestone> milestonesForAggregates = null;
        if (dueDateChanged) {
            milestonesForAggregates = projectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(project.getId());
            milestonePolicyEngine.validateChronologyForUpdate(milestonesForAggregates, milestone.getId(), requestedDueDate);
        }

        Instant now = Instant.now();
        milestone.setTitle(request.getTitle().trim());
        milestone.setDescription(NormalizationUtils.trimToNull(request.getDescription()));
        milestone.setDueDate(requestedDueDate);
        milestone.setStatus(milestoneStatus);
        milestone.setUpdatedAt(now);
        projectMilestoneRepository.save(milestone);

        if (statusChanged || dueDateChanged) {
            if (milestonesForAggregates != null) {
                for (ProjectMilestone candidate : milestonesForAggregates) {
                    if (Objects.equals(candidate.getId(), milestone.getId())) {
                        candidate.setDueDate(requestedDueDate);
                        candidate.setStatus(milestoneStatus);
                        break;
                    }
                }
            }
            List<ProjectMilestone> orderedMilestones = milestonesForAggregates != null
                    ? milestonesForAggregates
                    : projectMilestoneAggregateService.loadOrderedMilestones(project.getId());
            projectMilestoneAggregateService.applyTo(project, orderedMilestones);
        }
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        projectRepository.save(project);

        return projectDtoMapper.toProjectDetail(project);
    }

    private UUID parseProjectId(String projectId) {
        return EntityIdParser.parseOrNotFound(projectId);
    }

    private UUID parseMilestoneId(String milestoneId) {
        return EntityIdParser.parseOrNotFound(milestoneId);
    }
}
