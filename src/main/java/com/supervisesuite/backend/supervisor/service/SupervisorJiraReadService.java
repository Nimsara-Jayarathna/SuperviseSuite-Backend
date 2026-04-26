package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.common.error.ApiErrorDetail;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.EntityIdParser;
import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.dto.JiraHierarchyDto;
import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.jira.JiraHealthService;
import com.supervisesuite.backend.projects.service.jira.JiraIssueSyncService;
import com.supervisesuite.backend.projects.service.jira.JiraSprintProgressService;
import com.supervisesuite.backend.projects.service.jira.JiraWorkloadService;
import com.supervisesuite.backend.users.entity.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupervisorJiraReadService {

    private final ProjectRepository projectRepository;
    private final ProjectJiraIntegrationRepository projectJiraIntegrationRepository;
    private final ProjectJiraIssueRepository projectJiraIssueRepository;
    private final JiraIssueSyncService jiraIssueSyncService;
    private final JiraHealthService jiraHealthService;
    private final JiraSprintProgressService jiraSprintProgressService;
    private final JiraWorkloadService jiraWorkloadService;
    private final ProjectAccessGuard projectAccessGuard;

    SupervisorJiraReadService(
            ProjectRepository projectRepository,
            ProjectJiraIntegrationRepository projectJiraIntegrationRepository,
            ProjectJiraIssueRepository projectJiraIssueRepository,
            JiraIssueSyncService jiraIssueSyncService,
            JiraHealthService jiraHealthService,
            JiraSprintProgressService jiraSprintProgressService,
            JiraWorkloadService jiraWorkloadService,
            ProjectAccessGuard projectAccessGuard) {
        this.projectRepository = projectRepository;
        this.projectJiraIntegrationRepository = projectJiraIntegrationRepository;
        this.projectJiraIssueRepository = projectJiraIssueRepository;
        this.jiraIssueSyncService = jiraIssueSyncService;
        this.jiraHealthService = jiraHealthService;
        this.jiraSprintProgressService = jiraSprintProgressService;
        this.jiraWorkloadService = jiraWorkloadService;
        this.projectAccessGuard = projectAccessGuard;
    }

    @Transactional(readOnly = true)
    JiraHealthDto getJiraHealthOverview(User supervisor, String projectId) {
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
        return jiraHealthService.getHealthOverview(parsedProjectId);
    }

    @Transactional(readOnly = true)
    JiraSprintProgressDto getJiraSprintProgress(User supervisor, String projectId) {
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
        return jiraSprintProgressService.getSprintProgress(parsedProjectId);
    }

    @Transactional(readOnly = true)
    JiraWorkloadDto getJiraWorkload(User supervisor, String projectId) {
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
        return jiraWorkloadService.getWorkload(parsedProjectId);
    }

    @Transactional(readOnly = true)
    JiraHierarchyDto getJiraHierarchy(User supervisor, String projectId) {
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        List<ProjectJiraIssue> issues = projectJiraIssueRepository.findAllByProjectId(parsedProjectId);

        Map<String, JiraHierarchyDto.JiraHierarchyNodeDto> nodeMap = new LinkedHashMap<>();
        for (ProjectJiraIssue issue : issues) {
            JiraHierarchyDto.JiraHierarchyNodeDto node = new JiraHierarchyDto.JiraHierarchyNodeDto();
            node.setIssueKey(issue.getIssueKey());
            node.setSummary(issue.getSummary());
            node.setIssueType(issue.getIssueType());
            node.setStatus(issue.getStatusName());
            node.setPriority(issue.getPriorityName());
            node.setAssigneeDisplayName(issue.getAssigneeDisplayName());
            node.setStoryPoints(issue.getStoryPoints() == null ? null : issue.getStoryPoints().intValue());
            node.setChildren(new ArrayList<>());
            nodeMap.put(issue.getIssueKey(), node);
        }

        List<JiraHierarchyDto.JiraHierarchyNodeDto> roots = new ArrayList<>();
        List<JiraHierarchyDto.JiraHierarchyNodeDto> orphans = new ArrayList<>();

        for (ProjectJiraIssue issue : issues) {
            JiraHierarchyDto.JiraHierarchyNodeDto node = nodeMap.get(issue.getIssueKey());
            String parentKey = issue.getParentKey();

            if (parentKey == null || parentKey.isBlank()) {
                roots.add(node);
            } else if (nodeMap.containsKey(parentKey)) {
                nodeMap.get(parentKey).getChildren().add(node);
            } else {
                orphans.add(node);
            }
        }

        JiraHierarchyDto dto = new JiraHierarchyDto();
        dto.setRoots(roots);
        dto.setOrphans(orphans);
        return dto;
    }

    JiraHealthDto refreshProjectJiraData(User supervisor, String projectId) {
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parseProjectId(projectId));

        ProjectJiraIntegration activeIntegration = projectJiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(project.getId())
                .orElse(null);
        if (activeIntegration == null) {
            throw new ValidationException(
                    "Jira is not connected for this project.",
                    List.of(new ApiErrorDetail("jira", "Jira is not connected for this project.")));
        }

        Instant now = Instant.now();

        jiraIssueSyncService.syncProjectIssues(project.getId());

        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        projectRepository.save(project);

        return jiraHealthService.getHealthOverview(project.getId());
    }

    private UUID parseProjectId(String projectId) {
        return EntityIdParser.parseOrNotFound(projectId);
    }
}
