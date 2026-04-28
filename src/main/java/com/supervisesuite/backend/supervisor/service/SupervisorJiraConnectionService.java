package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.common.util.EntityIdParser;
import com.supervisesuite.backend.projects.dto.JiraAuthUrlDto;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteRequestDto;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteResultDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.users.entity.User;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupervisorJiraConnectionService {

    private final ProjectAccessGuard projectAccessGuard;
    private final SupervisorJiraOAuthStartService oauthStartService;
    private final SupervisorJiraOAuthCompletionService oauthCompletionService;
    private final SupervisorJiraIntegrationWriter integrationWriter;

    SupervisorJiraConnectionService(
            ProjectAccessGuard projectAccessGuard,
            SupervisorJiraOAuthStartService oauthStartService,
            SupervisorJiraOAuthCompletionService oauthCompletionService,
            SupervisorJiraIntegrationWriter integrationWriter) {
        this.projectAccessGuard = projectAccessGuard;
        this.oauthStartService = oauthStartService;
        this.oauthCompletionService = oauthCompletionService;
        this.integrationWriter = integrationWriter;
    }

    @Transactional
    JiraAuthUrlDto getProjectJiraAuthUrl(User supervisor, String projectId) {
        UUID parsedProjectId = parseProjectId(projectId);
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
        return oauthStartService.getProjectJiraAuthUrl(supervisor, parsedProjectId, project);
    }

    @Transactional
    JiraOAuthCompleteResultDto completeJiraOAuth(User supervisor, JiraOAuthCompleteRequestDto request) {
        return oauthCompletionService.completeJiraOAuth(supervisor, request);
    }

    @Transactional
    SupervisorProjectDetailDto disconnectProjectJira(User supervisor, String projectId) {
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parseProjectId(projectId));
        return integrationWriter.disconnectProjectJira(project);
    }

    private UUID parseProjectId(String projectId) {
        return EntityIdParser.parseOrNotFound(projectId);
    }
}
