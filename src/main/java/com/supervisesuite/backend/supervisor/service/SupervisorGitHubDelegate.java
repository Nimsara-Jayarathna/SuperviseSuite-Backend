package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.common.util.EntityIdParser;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestContinueDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestValidationDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedAcknowledgeDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedSummaryDto;
import com.supervisesuite.backend.projects.dto.GitHubAvailableRepositoriesDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallStartDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryPageDto;
import com.supervisesuite.backend.projects.dto.LinkProjectGitHubRepositoryRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubAccessMetadata;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryListingDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import com.supervisesuite.backend.projects.service.ProjectService;
import com.supervisesuite.backend.projects.service.githubv2.AccessRequestService;
import com.supervisesuite.backend.projects.service.githubv2.AccessSourceService;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.service.githubv2.SetupCallbackService;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupervisorGitHubDelegate {

    private final ProjectAccessGuard projectAccessGuard;
    private final ProjectService projectService;
    private final GitHubAppIntegrationService gitHubAppIntegrationService;
    private final SetupCallbackService setupCallbackService;
    private final RepositoryLinkService repositoryLinkService;
    private final AccessSourceService accessSourceService;
    private final AccessRequestService accessRequestService;
    private final ProjectRepository projectRepository;
    private final SupervisorProjectDtoMapper projectDtoMapper;

    SupervisorGitHubDelegate(
            ProjectAccessGuard projectAccessGuard,
            ProjectService projectService,
            GitHubAppIntegrationService gitHubAppIntegrationService,
            SetupCallbackService setupCallbackService,
            RepositoryLinkService repositoryLinkService,
            AccessSourceService accessSourceService,
            AccessRequestService accessRequestService,
            ProjectRepository projectRepository,
            SupervisorProjectDtoMapper projectDtoMapper) {
        this.projectAccessGuard = projectAccessGuard;
        this.projectService = projectService;
        this.gitHubAppIntegrationService = gitHubAppIntegrationService;
        this.setupCallbackService = setupCallbackService;
        this.repositoryLinkService = repositoryLinkService;
        this.accessSourceService = accessSourceService;
        this.accessRequestService = accessRequestService;
        this.projectRepository = projectRepository;
        this.projectDtoMapper = projectDtoMapper;
    }

    @Transactional(readOnly = true)
    ProjectGitHubDashboardDto getProjectGitHubDashboard(
            User supervisor,
            String projectId,
            String linkedRepositoryId) {
        Project project = requireProject(supervisor, projectId);
        UUID parsedLinkedRepositoryId = parseLinkedRepositoryId(linkedRepositoryId);
        return projectService.getGitHubDashboard(project.getId(), null, parsedLinkedRepositoryId);
    }

    @Transactional(readOnly = true)
    ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getProjectGitHubActivityPage(
            User supervisor,
            String projectId,
            String linkedRepositoryId,
            int page,
            int size) {
        Project project = requireProject(supervisor, projectId);
        UUID parsedLinkedRepositoryId = parseLinkedRepositoryId(linkedRepositoryId);
        return projectService.getGitHubActivityPage(project.getId(), null, parsedLinkedRepositoryId, page, size);
    }

    @Transactional(readOnly = true)
    ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getProjectGitHubContributorsPage(
            User supervisor,
            String projectId,
            String linkedRepositoryId,
            int page,
            int size) {
        Project project = requireProject(supervisor, projectId);
        UUID parsedLinkedRepositoryId = parseLinkedRepositoryId(linkedRepositoryId);
        return projectService.getGitHubContributorsPage(project.getId(), null, parsedLinkedRepositoryId, page, size);
    }

    @Transactional(readOnly = true)
    GitHubInstallationRepositoryPageDto getGitHubInstallationRepositories(
            User supervisor,
            String projectId,
            Long installationId,
            int page,
            Integer size) {
        Project project = requireProject(supervisor, projectId);
        return projectService.getInstallationRepositories(project.getId(), installationId, supervisor.getId(), page,
                size);
    }

    @Transactional(readOnly = true)
    ProjectGitHubRepositoryListingDto getProjectRepositoriesInventory(
            String authenticatedUserId,
            User supervisor,
            String projectId) {
        Project project = requireProject(supervisor, projectId);

        List<com.supervisesuite.backend.projects.dto.GitHubAccessSourceDto> sources =
                accessSourceService.getProjectAccessSources(project.getId());

        List<GitHubAvailableRepositoriesDto> inventory = sources.stream()
                .map(source -> repositoryLinkService.getAvailableRepositories(source.getId(), authenticatedUserId))
                .toList();

        return new ProjectGitHubRepositoryListingDto(project.getId().toString(), inventory);
    }

    @Transactional
    GitHubAccessRequestCreateDto createGitHubRepositoryAccessRequest(User supervisor, String projectId) {
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
        return gitHubAppIntegrationService.createProjectAccessRequest(parsedProjectId, supervisor.getId());
    }

    @Transactional
    GitHubAccessRequestValidationDto validateGitHubRepositoryAccessRequest(
            User supervisor,
            String projectId,
            String requestToken) {
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
        return gitHubAppIntegrationService.validateProjectAccessRequest(
                parsedProjectId,
                supervisor.getId(),
                requestToken);
    }

    @Transactional
    GitHubAccessRequestContinueDto continueGitHubRepositoryAccessRequest(
            User supervisor,
            String projectId,
            String requestToken) {
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
        return gitHubAppIntegrationService.continueProjectAccessRequest(
                parsedProjectId,
                supervisor.getId(),
                requestToken);
    }

    @Transactional(readOnly = true)
    String buildGitHubSetupStartUrl(User supervisor, String projectId) {
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        GitHubInstallStartDto setup = setupCallbackService.startDirectInstall(
                parsedProjectId.toString(),
                supervisor.getId().toString());
        return setup.getGithubAuthorizeUrl();
    }

    @Transactional
    ProjectGitHubRepositoryLinkDto linkProjectGitHubRepository(
            User supervisor,
            String projectId,
            LinkProjectGitHubRepositoryRequest request) {
        Project project = requireProject(supervisor, projectId);

        ProjectGitHubRepositoryLinkDto linkedRepository = projectService.linkProjectToInstallationRepository(
                project.getId(),
                request.getInstallationId(),
                request.getRepositoryId(),
                supervisor.getId());

        touchProject(project);

        return linkedRepository;
    }

    @Transactional
    SupervisorProjectDetailDto removeProjectGitHubAccessAuthorization(User supervisor, String projectId) {
        Project project = requireProject(supervisor, projectId);

        repositoryLinkService.disconnectAllLinks(project.getId());
        Project savedProject = touchProject(project);

        return projectDtoMapper.toProjectDetail(savedProject);
    }

    @Transactional(noRollbackFor = com.supervisesuite.backend.projects.integration.github.GitHubInstallationDisconnectedException.class)
    void refreshProjectGitHubData(User supervisor, String projectId) {
        Project project = requireProject(supervisor, projectId);

        ProjectGitHubAccessMetadata accessMetadata = repositoryLinkService.resolveLink(project.getId());
        String effectiveUrl = accessMetadata != null ? accessMetadata.primaryRepositoryUrl() : null;

        projectService.refreshGitHubData(project.getId(), effectiveUrl);
    }

    @Transactional(readOnly = true)
    GitHubAccessUpdatedSummaryDto getGitHubAccessUpdatedSummary(User supervisor, String projectId) {
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        return accessRequestService.getPendingSummary(parsedProjectId);
    }

    @Transactional
    GitHubAccessUpdatedAcknowledgeDto acknowledgeGitHubAccessUpdated(User supervisor, String projectId) {
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        accessRequestService.acknowledgePending(parsedProjectId);
        return new GitHubAccessUpdatedAcknowledgeDto(parsedProjectId);
    }

    private Project requireProject(User supervisor, String projectId) {
        return projectAccessGuard.requireSupervisorOwnsProject(supervisor, parseProjectId(projectId));
    }

    private UUID parseProjectId(String projectId) {
        return EntityIdParser.parseOrNotFound(projectId);
    }

    private UUID parseLinkedRepositoryId(String linkedRepositoryId) {
        return EntityIdParser.parseOrNull(linkedRepositoryId, "linkedRepositoryId");
    }

    private Project touchProject(Project project) {
        Instant now = Instant.now();
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        Project savedProject = projectRepository.save(project);
        return savedProject != null ? savedProject : project;
    }
}
