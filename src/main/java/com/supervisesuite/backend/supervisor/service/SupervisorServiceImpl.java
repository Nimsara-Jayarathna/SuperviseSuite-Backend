package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestContinueDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestValidationDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedAcknowledgeDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedSummaryDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryPageDto;
import com.supervisesuite.backend.projects.dto.JiraAuthUrlDto;
import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.dto.JiraHierarchyDto;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteRequestDto;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteResultDto;
import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import com.supervisesuite.backend.projects.dto.LinkProjectGitHubRepositoryRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryListingDto;
import com.supervisesuite.backend.projects.dto.UpdateRepositoryRequest;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraOAuthStateRepository;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import com.supervisesuite.backend.projects.service.ProjectService;
import com.supervisesuite.backend.projects.service.githubv2.AccessRequestService;
import com.supervisesuite.backend.projects.service.githubv2.AccessSourceService;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.service.githubv2.SetupCallbackService;
import com.supervisesuite.backend.projects.service.jira.JiraHealthService;
import com.supervisesuite.backend.projects.service.jira.JiraIssueSyncService;
import com.supervisesuite.backend.projects.service.jira.JiraSprintProgressService;
import com.supervisesuite.backend.projects.service.jira.JiraTokenEncryptionService;
import com.supervisesuite.backend.projects.service.jira.JiraWorkloadService;
import com.supervisesuite.backend.projects.service.milestones.MilestonePolicyEngine;
import com.supervisesuite.backend.projects.service.milestones.ProjectMilestoneAggregateService;
import com.supervisesuite.backend.projectfiles.service.ProjectFileService;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMembersRequest;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorDashboardDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectSummaryDto;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectStatusRequest;
import com.supervisesuite.backend.meetings.dto.CreateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.CreateMeetingRecordRequest;
import com.supervisesuite.backend.meetings.dto.MeetingChannelDto;
import com.supervisesuite.backend.meetings.dto.MeetingRecordDto;
import com.supervisesuite.backend.meetings.dto.UpdateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.UpdateMeetingRecordRequest;
import com.supervisesuite.backend.meetings.service.MeetingChannelService;
import com.supervisesuite.backend.meetings.service.MeetingRecordService;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
class SupervisorServiceImpl implements SupervisorService {

    private final ProjectAccessGuard projectAccessGuard;
    private final SupervisorProjectQueryService projectQueryService;
    private final SupervisorProjectCommandService projectCommandService;
    private final SupervisorProjectMemberService projectMemberService;
    private final SupervisorProjectMilestoneService projectMilestoneService;
    private final SupervisorGitHubDelegate gitHubDelegate;
    private final SupervisorJiraConnectionService jiraConnectionService;
    private final SupervisorJiraReadService jiraReadService;
    private final SupervisorMeetingDelegate meetingDelegate;

    @Autowired
    SupervisorServiceImpl(
            ProjectAccessGuard projectAccessGuard,
            SupervisorProjectQueryService projectQueryService,
            SupervisorProjectCommandService projectCommandService,
            SupervisorProjectMemberService projectMemberService,
            SupervisorProjectMilestoneService projectMilestoneService,
            SupervisorGitHubDelegate gitHubDelegate,
            SupervisorJiraConnectionService jiraConnectionService,
            SupervisorJiraReadService jiraReadService,
            SupervisorMeetingDelegate meetingDelegate) {
        this.projectAccessGuard = projectAccessGuard;
        this.projectQueryService = projectQueryService;
        this.projectCommandService = projectCommandService;
        this.projectMemberService = projectMemberService;
        this.projectMilestoneService = projectMilestoneService;
        this.gitHubDelegate = gitHubDelegate;
        this.jiraConnectionService = jiraConnectionService;
        this.jiraReadService = jiraReadService;
        this.meetingDelegate = meetingDelegate;
    }

    SupervisorServiceImpl(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectMilestoneRepository projectMilestoneRepository,
            ProjectService projectService,
            GitHubAppIntegrationService gitHubAppIntegrationService,
            SetupCallbackService setupCallbackService,
            RepositoryLinkService repositoryLinkService,
            AccessSourceService accessSourceService,
            AccessRequestService accessRequestService,
            JiraProperties jiraProperties,
            ProjectJiraIntegrationRepository projectJiraIntegrationRepository,
            ProjectJiraOAuthStateRepository projectJiraOAuthStateRepository,
            JiraTokenEncryptionService jiraTokenEncryptionService,
            ProjectJiraIssueRepository projectJiraIssueRepository,
            JiraIssueSyncService jiraIssueSyncService,
            JiraHealthService jiraHealthService,
            JiraSprintProgressService jiraSprintProgressService,
            JiraWorkloadService jiraWorkloadService,
            ProjectFileService projectFileService,
            MeetingChannelService meetingChannelService,
            MeetingRecordService meetingRecordService,
            RestClient.Builder restClientBuilder,
            MilestonePolicyEngine milestonePolicyEngine,
            ProjectMilestoneAggregateService projectMilestoneAggregateService,
            ProjectAccessGuard projectAccessGuard) {
        this(
                projectAccessGuard,
                new SupervisorProjectQueryService(
                        projectRepository,
                        projectJiraIntegrationRepository,
                        projectJiraIssueRepository,
                        projectFileService,
                        projectAccessGuard,
                        new SupervisorProjectDtoMapper(
                                userRepository,
                                projectMemberRepository,
                                projectMilestoneRepository,
                                projectService,
                                repositoryLinkService,
                                projectJiraIntegrationRepository,
                                milestonePolicyEngine)),
                buildProjectCommandService(
                        userRepository,
                        projectRepository,
                        projectMemberRepository,
                        projectMilestoneRepository,
                        repositoryLinkService,
                        milestonePolicyEngine,
                        projectMilestoneAggregateService,
                        projectAccessGuard,
                        projectService,
                        projectJiraIntegrationRepository),
                buildProjectMemberService(
                        userRepository,
                        projectMemberRepository,
                        projectRepository,
                        projectAccessGuard,
                        projectMilestoneRepository,
                        projectService,
                        repositoryLinkService,
                        projectJiraIntegrationRepository,
                        milestonePolicyEngine),
                new SupervisorProjectMilestoneService(
                        projectRepository,
                        projectMilestoneRepository,
                        milestonePolicyEngine,
                        projectMilestoneAggregateService,
                        projectAccessGuard,
                        new SupervisorProjectDtoMapper(
                                userRepository,
                                projectMemberRepository,
                                projectMilestoneRepository,
                                projectService,
                                repositoryLinkService,
                                projectJiraIntegrationRepository,
                                milestonePolicyEngine)),
                new SupervisorGitHubDelegate(
                        projectAccessGuard,
                        projectService,
                        gitHubAppIntegrationService,
                        setupCallbackService,
                        repositoryLinkService,
                        accessSourceService,
                        accessRequestService,
                        projectRepository,
                        new SupervisorProjectDtoMapper(
                                userRepository,
                                projectMemberRepository,
                                projectMilestoneRepository,
                                projectService,
                                repositoryLinkService,
                                projectJiraIntegrationRepository,
                                milestonePolicyEngine)),
                new SupervisorJiraConnectionService(
                        jiraProperties,
                        projectRepository,
                        projectJiraIntegrationRepository,
                        projectJiraOAuthStateRepository,
                        jiraTokenEncryptionService,
                        projectJiraIssueRepository,
                        jiraIssueSyncService,
                        projectAccessGuard,
                        new SupervisorProjectDtoMapper(
                                userRepository,
                                projectMemberRepository,
                                projectMilestoneRepository,
                                projectService,
                                repositoryLinkService,
                                projectJiraIntegrationRepository,
                                milestonePolicyEngine),
                        restClientBuilder),
                new SupervisorJiraReadService(
                        projectRepository,
                        projectJiraIntegrationRepository,
                        projectJiraIssueRepository,
                        jiraIssueSyncService,
                        jiraHealthService,
                        jiraSprintProgressService,
                        jiraWorkloadService,
                        projectAccessGuard),
                new SupervisorMeetingDelegate(meetingChannelService, meetingRecordService));
    }

    private static SupervisorProjectMemberService buildProjectMemberService(
            UserRepository userRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectRepository projectRepository,
            ProjectAccessGuard projectAccessGuard,
            ProjectMilestoneRepository projectMilestoneRepository,
            ProjectService projectService,
            RepositoryLinkService repositoryLinkService,
            ProjectJiraIntegrationRepository projectJiraIntegrationRepository,
            MilestonePolicyEngine milestonePolicyEngine) {
        return new SupervisorProjectMemberService(
                userRepository,
                projectMemberRepository,
                projectRepository,
                projectAccessGuard,
                new SupervisorProjectDtoMapper(
                        userRepository,
                        projectMemberRepository,
                        projectMilestoneRepository,
                        projectService,
                        repositoryLinkService,
                        projectJiraIntegrationRepository,
                        milestonePolicyEngine));
    }

    private static SupervisorProjectCommandService buildProjectCommandService(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectMilestoneRepository projectMilestoneRepository,
            RepositoryLinkService repositoryLinkService,
            MilestonePolicyEngine milestonePolicyEngine,
            ProjectMilestoneAggregateService projectMilestoneAggregateService,
            ProjectAccessGuard projectAccessGuard,
            ProjectService projectService,
            ProjectJiraIntegrationRepository projectJiraIntegrationRepository) {
        SupervisorProjectDtoMapper mapper = new SupervisorProjectDtoMapper(
                userRepository,
                projectMemberRepository,
                projectMilestoneRepository,
                projectService,
                repositoryLinkService,
                projectJiraIntegrationRepository,
                milestonePolicyEngine);
        SupervisorProjectMemberService memberService = new SupervisorProjectMemberService(
                userRepository,
                projectMemberRepository,
                projectRepository,
                projectAccessGuard,
                mapper);
        return new SupervisorProjectCommandService(
                projectRepository,
                projectMemberRepository,
                projectMilestoneRepository,
                repositoryLinkService,
                milestonePolicyEngine,
                projectMilestoneAggregateService,
                projectAccessGuard,
                mapper,
                memberService);
    }

    @Override
    public SupervisorDashboardDto getDashboard(String authenticatedUserId) {
        return projectQueryService.getDashboard(resolveSupervisor(authenticatedUserId));
    }

    @Override
    public List<SupervisorProjectSummaryDto> getProjects(String authenticatedUserId) {
        return projectQueryService.getProjects(resolveSupervisor(authenticatedUserId));
    }

    @Override
    public SupervisorProjectDetailDto getProjectById(String authenticatedUserId, String projectId) {
        return projectQueryService.getProjectById(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public ProjectGitHubDashboardDto getProjectGitHubDashboard(
            String authenticatedUserId,
            String projectId,
            String linkedRepositoryId) {
        return gitHubDelegate.getProjectGitHubDashboard(resolveSupervisor(authenticatedUserId), projectId, linkedRepositoryId);
    }

    @Override
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getProjectGitHubActivityPage(
            String authenticatedUserId,
            String projectId,
            String linkedRepositoryId,
            int page,
            int size) {
        return gitHubDelegate.getProjectGitHubActivityPage(resolveSupervisor(authenticatedUserId), projectId, linkedRepositoryId, page, size);
    }

    @Override
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getProjectGitHubContributorsPage(
            String authenticatedUserId,
            String projectId,
            String linkedRepositoryId,
            int page,
            int size) {
        return gitHubDelegate.getProjectGitHubContributorsPage(resolveSupervisor(authenticatedUserId), projectId, linkedRepositoryId, page, size);
    }

    @Override
    public GitHubInstallationRepositoryPageDto getGitHubInstallationRepositories(
            String authenticatedUserId,
            String projectId,
            Long installationId,
            int page,
            Integer size) {
        return gitHubDelegate.getGitHubInstallationRepositories(resolveSupervisor(authenticatedUserId), projectId, installationId, page, size);
    }

    @Override
    public ProjectGitHubRepositoryListingDto getProjectRepositoriesInventory(
            String authenticatedUserId,
            String projectId) {
        return gitHubDelegate.getProjectRepositoriesInventory(authenticatedUserId, resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public GitHubAccessRequestCreateDto createGitHubRepositoryAccessRequest(
            String authenticatedUserId,
            String projectId) {
        return gitHubDelegate.createGitHubRepositoryAccessRequest(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public GitHubAccessRequestValidationDto validateGitHubRepositoryAccessRequest(
            String authenticatedUserId,
            String projectId,
            String requestToken) {
        return gitHubDelegate.validateGitHubRepositoryAccessRequest(resolveSupervisor(authenticatedUserId), projectId, requestToken);
    }

    @Override
    public GitHubAccessRequestContinueDto continueGitHubRepositoryAccessRequest(
            String authenticatedUserId,
            String projectId,
            String requestToken) {
        return gitHubDelegate.continueGitHubRepositoryAccessRequest(resolveSupervisor(authenticatedUserId), projectId, requestToken);
    }

    @Override
    public String buildGitHubSetupStartUrl(String authenticatedUserId, String projectId) {
        return gitHubDelegate.buildGitHubSetupStartUrl(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public ProjectGitHubRepositoryLinkDto linkProjectGitHubRepository(
            String authenticatedUserId,
            String projectId,
            LinkProjectGitHubRepositoryRequest request) {
        return gitHubDelegate.linkProjectGitHubRepository(resolveSupervisor(authenticatedUserId), projectId, request);
    }

    @Override
    public SupervisorProjectDetailDto removeProjectGitHubAccessAuthorization(
            String authenticatedUserId,
            String projectId) {
        return gitHubDelegate.removeProjectGitHubAccessAuthorization(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public void refreshProjectGitHubData(String authenticatedUserId, String projectId) {
        gitHubDelegate.refreshProjectGitHubData(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public SupervisorProjectDetailDto updateProject(
            String authenticatedUserId,
            String projectId,
            UpdateSupervisorProjectRequest request) {
        return projectCommandService.updateProject(resolveSupervisor(authenticatedUserId), projectId, request);
    }

    @Override
    public SupervisorProjectDetailDto updateProjectStatus(
            String authenticatedUserId,
            String projectId,
            UpdateSupervisorProjectStatusRequest request) {
        return projectCommandService.updateProjectStatus(resolveSupervisor(authenticatedUserId), projectId, request);
    }

    @Override
    public SupervisorProjectDetailDto updateRepository(
            String authenticatedUserId,
            String projectId,
            UpdateRepositoryRequest request) {
        return projectCommandService.updateRepository(resolveSupervisor(authenticatedUserId), projectId, request);
    }

    @Override
    public SupervisorProjectDetailDto addProjectMembers(
            String authenticatedUserId,
            String projectId,
            AddSupervisorProjectMembersRequest request) {
        return projectMemberService.addProjectMembers(resolveSupervisor(authenticatedUserId), projectId, request);
    }

    @Override
    public SupervisorProjectDetailDto addProjectMilestone(
            String authenticatedUserId,
            String projectId,
            AddSupervisorProjectMilestoneRequest request) {
        return projectMilestoneService.addProjectMilestone(resolveSupervisor(authenticatedUserId), projectId, request);
    }

    @Override
    public SupervisorProjectDetailDto updateProjectMilestone(
            String authenticatedUserId,
            String projectId,
            String milestoneId,
            UpdateSupervisorProjectMilestoneRequest request) {
        return projectMilestoneService.updateProjectMilestone(resolveSupervisor(authenticatedUserId), projectId, milestoneId, request);
    }

    @Override
    public List<StudentSearchResultDto> searchStudents(String query) {
        return projectMemberService.searchStudents(query);
    }

    @Override
    public CreateSupervisorProjectResponse createProject(
            String authenticatedUserId,
            CreateSupervisorProjectRequest request) {
        return projectCommandService.createProject(resolveSupervisor(authenticatedUserId), request);
    }

    @Override
    public GitHubAccessUpdatedSummaryDto getGitHubAccessUpdatedSummary(String authenticatedUserId, String projectId) {
        return gitHubDelegate.getGitHubAccessUpdatedSummary(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public GitHubAccessUpdatedAcknowledgeDto acknowledgeGitHubAccessUpdated(String authenticatedUserId, String projectId) {
        return gitHubDelegate.acknowledgeGitHubAccessUpdated(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public JiraAuthUrlDto getProjectJiraAuthUrl(String authenticatedUserId, String projectId) {
        return jiraConnectionService.getProjectJiraAuthUrl(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public JiraOAuthCompleteResultDto completeJiraOAuth(String authenticatedUserId, JiraOAuthCompleteRequestDto request) {
        return jiraConnectionService.completeJiraOAuth(resolveSupervisor(authenticatedUserId), request);
    }

    @Override
    public SupervisorProjectDetailDto disconnectProjectJira(String authenticatedUserId, String projectId) {
        return jiraConnectionService.disconnectProjectJira(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public JiraHealthDto getJiraHealthOverview(String authenticatedUserId, String projectId) {
        return jiraReadService.getJiraHealthOverview(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public JiraSprintProgressDto getJiraSprintProgress(String authenticatedUserId, String projectId) {
        return jiraReadService.getJiraSprintProgress(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public JiraWorkloadDto getJiraWorkload(String authenticatedUserId, String projectId) {
        return jiraReadService.getJiraWorkload(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public JiraHierarchyDto getJiraHierarchy(String authenticatedUserId, String projectId) {
        return jiraReadService.getJiraHierarchy(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public JiraHealthDto refreshProjectJiraData(String authenticatedUserId, String projectId) {
        return jiraReadService.refreshProjectJiraData(resolveSupervisor(authenticatedUserId), projectId);
    }

    @Override
    public List<MeetingChannelDto> getProjectMeetingChannels(String authenticatedUserId, String projectId) {
        return meetingDelegate.getProjectMeetingChannels(authenticatedUserId, projectId);
    }

    @Override
    public MeetingChannelDto addProjectMeetingChannel(
            String authenticatedUserId,
            String projectId,
            CreateMeetingChannelRequest request) {
        return meetingDelegate.addProjectMeetingChannel(authenticatedUserId, projectId, request);
    }

    @Override
    public MeetingChannelDto updateProjectMeetingChannel(
            String authenticatedUserId,
            String projectId,
            String channelId,
            UpdateMeetingChannelRequest request) {
        return meetingDelegate.updateProjectMeetingChannel(authenticatedUserId, projectId, channelId, request);
    }

    @Override
    public void deleteProjectMeetingChannel(String authenticatedUserId, String projectId, String channelId) {
        meetingDelegate.deleteProjectMeetingChannel(authenticatedUserId, projectId, channelId);
    }

    @Override
    public MeetingChannelDto approveProjectMeetingChannel(String authenticatedUserId, String projectId, String channelId) {
        return meetingDelegate.approveProjectMeetingChannel(authenticatedUserId, projectId, channelId);
    }

    @Override
    public List<MeetingRecordDto> getProjectMeetingRecords(String authenticatedUserId, String projectId) {
        return meetingDelegate.getProjectMeetingRecords(authenticatedUserId, projectId);
    }

    @Override
    public MeetingRecordDto addProjectMeetingRecord(
            String authenticatedUserId,
            String projectId,
            CreateMeetingRecordRequest request) {
        return meetingDelegate.addProjectMeetingRecord(authenticatedUserId, projectId, request);
    }

    @Override
    public MeetingRecordDto updateProjectMeetingRecord(
            String authenticatedUserId,
            String projectId,
            String recordId,
            UpdateMeetingRecordRequest request) {
        return meetingDelegate.updateProjectMeetingRecord(authenticatedUserId, projectId, recordId, request);
    }

    @Override
    public void deleteProjectMeetingRecord(String authenticatedUserId, String projectId, String recordId) {
        meetingDelegate.deleteProjectMeetingRecord(authenticatedUserId, projectId, recordId);
    }

    @Override
    public MeetingRecordDto approveProjectMeetingRecord(String authenticatedUserId, String projectId, String recordId) {
        return meetingDelegate.approveProjectMeetingRecord(authenticatedUserId, projectId, recordId);
    }

    private User resolveSupervisor(String authenticatedUserId) {
        return projectAccessGuard.requireSupervisor(authenticatedUserId);
    }
}
