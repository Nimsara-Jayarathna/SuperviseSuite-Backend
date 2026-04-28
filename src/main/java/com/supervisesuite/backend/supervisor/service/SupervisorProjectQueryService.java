package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.common.util.EntityIdParser;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileListDto;
import com.supervisesuite.backend.projectfiles.service.ProjectFileAccessRole;
import com.supervisesuite.backend.projectfiles.service.ProjectFileService;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.supervisor.dto.SupervisorDashboardDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectSummaryDto;
import com.supervisesuite.backend.users.entity.User;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupervisorProjectQueryService {

    private final ProjectRepository projectRepository;
    private final ProjectJiraIntegrationRepository projectJiraIntegrationRepository;
    private final ProjectJiraIssueRepository projectJiraIssueRepository;
    private final ProjectFileService projectFileService;
    private final ProjectAccessGuard projectAccessGuard;
    private final SupervisorProjectDtoMapper projectDtoMapper;

    SupervisorProjectQueryService(
            ProjectRepository projectRepository,
            ProjectJiraIntegrationRepository projectJiraIntegrationRepository,
            ProjectJiraIssueRepository projectJiraIssueRepository,
            ProjectFileService projectFileService,
            ProjectAccessGuard projectAccessGuard,
            SupervisorProjectDtoMapper projectDtoMapper) {
        this.projectRepository = projectRepository;
        this.projectJiraIntegrationRepository = projectJiraIntegrationRepository;
        this.projectJiraIssueRepository = projectJiraIssueRepository;
        this.projectFileService = projectFileService;
        this.projectAccessGuard = projectAccessGuard;
        this.projectDtoMapper = projectDtoMapper;
    }

    @Transactional(readOnly = true)
    SupervisorDashboardDto getDashboard(User supervisor) {
        List<Project> projects = projectRepository
                .findBySupervisorIdAndDeletedAtIsNullOrderByCreatedAtDesc(supervisor.getId());

        int planningProjects = 0;
        int activeProjects = 0;
        int atRiskProjects = 0;
        int behindProjects = 0;
        int completedProjects = 0;
        int upcomingMilestonesCount = 0;

        LocalDate today = LocalDate.now();
        LocalDate milestoneWindowEnd = today.plusDays(14);

        for (Project project : projects) {
            String lifecycleStatus = project.getStatus();
            if ("PLANNING".equals(lifecycleStatus)) {
                planningProjects++;
            } else if ("ACTIVE".equals(lifecycleStatus)) {
                activeProjects++;
            } else if ("AT_RISK".equals(lifecycleStatus)) {
                atRiskProjects++;
            } else if ("BEHIND".equals(lifecycleStatus)) {
                behindProjects++;
            } else if ("COMPLETED".equals(lifecycleStatus)) {
                completedProjects++;
            }

            LocalDate milestoneDate = project.getMilestoneDate();
            if (milestoneDate != null
                    && !milestoneDate.isBefore(today)
                    && !milestoneDate.isAfter(milestoneWindowEnd)) {
                upcomingMilestonesCount++;
            }
        }

        List<UUID> projectIds = projects.stream().map(Project::getId).toList();
        Set<UUID> connectedProjectIds = projectJiraIntegrationRepository
                .findAllByProjectIdInAndRevokedAtIsNull(projectIds)
                .stream()
                .map(com.supervisesuite.backend.projects.entity.ProjectJiraIntegration::getProjectId)
                .collect(java.util.stream.Collectors.toSet());

        int jiraAtRiskCount = 0;
        int jiraBehindCount = 0;
        Map<UUID, String> jiraIndicators = new HashMap<>();

        for (Project project : projects) {
            UUID pid = project.getId();
            if (!connectedProjectIds.contains(pid)) {
                jiraIndicators.put(pid, "NOT_CONNECTED");
                continue;
            }
            long total = projectJiraIssueRepository.countByProjectId(pid);
            if (total == 0) {
                jiraIndicators.put(pid, "HEALTHY");
                continue;
            }
            long done = projectJiraIssueRepository.countByProjectIdAndStatusCategoryKey(pid, "done");
            long overdue = projectJiraIssueRepository
                    .countByProjectIdAndDueDateBeforeAndStatusCategoryKeyNot(pid, today, "done");
            double completionPct = (double) done / total * 100.0;
            String indicator;
            if (overdue > 2) {
                indicator = "AT_RISK";
                jiraAtRiskCount++;
            } else if (completionPct < 50.0) {
                indicator = "BEHIND";
                jiraBehindCount++;
            } else {
                indicator = "HEALTHY";
            }
            jiraIndicators.put(pid, indicator);
        }

        List<SupervisorDashboardDto.ProjectItem> dashboardProjects = projects.stream()
                .map(p -> projectDtoMapper.toDashboardProjectItem(
                        p,
                        p.getMilestoneDate(),
                        jiraIndicators.getOrDefault(p.getId(), "NOT_CONNECTED")))
                .toList();

        List<SupervisorDashboardDto.ProjectItem> recentProjects = projects.stream()
                .sorted(Comparator
                        .comparing(Project::getLastActivityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Project::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(p -> projectDtoMapper.toDashboardProjectItem(
                        p,
                        p.getMilestoneDate(),
                        jiraIndicators.getOrDefault(p.getId(), "NOT_CONNECTED")))
                .toList();

        SupervisorDashboardDto dashboard = new SupervisorDashboardDto(
                projects.size(),
                planningProjects,
                activeProjects,
                atRiskProjects,
                behindProjects,
                completedProjects,
                upcomingMilestonesCount,
                dashboardProjects,
                recentProjects);
        dashboard.setJiraAtRiskCount(jiraAtRiskCount);
        dashboard.setJiraBehindCount(jiraBehindCount);
        return dashboard;
    }

    @Transactional(readOnly = true)
    List<SupervisorProjectSummaryDto> getProjects(User supervisor) {
        return projectRepository.findBySupervisorIdAndDeletedAtIsNullOrderByCreatedAtDesc(supervisor.getId())
                .stream()
                .map(projectDtoMapper::toProjectSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    SupervisorProjectDetailDto getProjectById(User supervisor, String projectId) {
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parseProjectId(projectId));

        SupervisorProjectDetailDto detail = projectDtoMapper.toProjectDetail(project);
        ProjectFileListDto files = projectFileService.listFiles(
                supervisor.getId().toString(),
                project.getId().toString(),
                ProjectFileAccessRole.SUPERVISOR);
        detail.setFiles(new SupervisorProjectDetailDto.Files(files.files(), files.config()));
        return detail;
    }

    private UUID parseProjectId(String projectId) {
        return EntityIdParser.parseOrNotFound(projectId);
    }
}
