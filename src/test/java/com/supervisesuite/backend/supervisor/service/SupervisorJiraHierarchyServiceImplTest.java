package com.supervisesuite.backend.supervisor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.dto.JiraHierarchyDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
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
import com.supervisesuite.backend.meetings.service.MeetingChannelService;
import com.supervisesuite.backend.meetings.service.MeetingRecordService;
import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class SupervisorJiraHierarchyServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectMilestoneRepository projectMilestoneRepository;
    @Mock
    private ProjectService projectService;
    @Mock
    private GitHubAppIntegrationService gitHubAppIntegrationService;
    @Mock
    private SetupCallbackService setupCallbackService;
    @Mock
    private RepositoryLinkService repositoryLinkService;
    @Mock
    private AccessSourceService accessSourceService;
    @Mock
    private AccessRequestService accessRequestService;
    @Mock
    private JiraProperties jiraProperties;
    @Mock
    private ProjectJiraIntegrationRepository projectJiraIntegrationRepository;
    @Mock
    private ProjectJiraOAuthStateRepository projectJiraOAuthStateRepository;
    @Mock
    private JiraTokenEncryptionService jiraTokenEncryptionService;
    @Mock
    private ProjectJiraIssueRepository projectJiraIssueRepository;
    @Mock
    private JiraIssueSyncService jiraIssueSyncService;
    @Mock
    private JiraHealthService jiraHealthService;
    @Mock
    private JiraSprintProgressService jiraSprintProgressService;
    @Mock
    private JiraWorkloadService jiraWorkloadService;
    @Mock
    private ProjectFileService projectFileService;
    @Mock
    private MeetingChannelService meetingChannelService;
    @Mock
    private MeetingRecordService meetingRecordService;
    @Mock
    private RestClient.Builder restClientBuilder;
    @Mock
    private RestClient restClient;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    private SupervisorServiceImpl service;
    private UUID supervisorId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        MilestonePolicyEngine milestonePolicyEngine = new MilestonePolicyEngine();
        ProjectMilestoneAggregateService projectMilestoneAggregateService =
                new ProjectMilestoneAggregateService(projectMilestoneRepository, milestonePolicyEngine);
        SupervisorProjectDtoMapper projectDtoMapper = new SupervisorProjectDtoMapper(
                userRepository,
                projectMemberRepository,
                projectMilestoneRepository,
                projectService,
                repositoryLinkService,
                projectJiraIntegrationRepository,
                milestonePolicyEngine);
        SupervisorProjectMemberService projectMemberService = new SupervisorProjectMemberService(
                userRepository,
                projectMemberRepository,
                projectRepository,
                projectAccessGuard,
                projectDtoMapper);
        service = new SupervisorServiceImpl(
                projectAccessGuard,
                new SupervisorProjectQueryService(
                        projectRepository,
                        projectJiraIntegrationRepository,
                        projectJiraIssueRepository,
                        projectFileService,
                        projectAccessGuard,
                        projectDtoMapper),
                new SupervisorProjectCommandService(
                        projectRepository,
                        projectMemberRepository,
                        projectMilestoneRepository,
                        repositoryLinkService,
                        milestonePolicyEngine,
                        projectMilestoneAggregateService,
                        projectAccessGuard,
                        projectDtoMapper,
                        projectMemberService),
                projectMemberService,
                new SupervisorProjectMilestoneService(
                        projectRepository,
                        projectMilestoneRepository,
                        milestonePolicyEngine,
                        projectMilestoneAggregateService,
                        projectAccessGuard,
                        projectDtoMapper),
                new SupervisorGitHubDelegate(
                        projectAccessGuard,
                        projectService,
                        gitHubAppIntegrationService,
                        setupCallbackService,
                        repositoryLinkService,
                        accessSourceService,
                        accessRequestService,
                        projectRepository,
                        projectDtoMapper),
                new SupervisorJiraConnectionService(
                        projectAccessGuard,
                        new SupervisorJiraOAuthStartService(
                                jiraProperties,
                                projectJiraIntegrationRepository,
                                projectJiraOAuthStateRepository,
                                new SecureTokenService()),
                        new SupervisorJiraOAuthCompletionService(
                                jiraProperties,
                                projectJiraOAuthStateRepository,
                                projectAccessGuard,
                                new SecureTokenService(),
                                new AtlassianOAuthClient(restClientBuilder),
                                new SupervisorJiraWorkspaceSelectionStore(),
                                new SupervisorJiraIntegrationWriter(
                                        projectRepository,
                                        projectJiraIntegrationRepository,
                                        jiraTokenEncryptionService,
                                        projectJiraIssueRepository,
                                        jiraIssueSyncService,
                                        projectDtoMapper)),
                        new SupervisorJiraIntegrationWriter(
                                projectRepository,
                                projectJiraIntegrationRepository,
                                jiraTokenEncryptionService,
                                projectJiraIssueRepository,
                                jiraIssueSyncService,
                                projectDtoMapper)),
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

        supervisorId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        User supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setRole(Roles.SUPERVISOR);
        lenient().when(projectAccessGuard.requireSupervisor(supervisorId.toString())).thenReturn(supervisor);

        Project project = new Project();
        project.setId(projectId);
        lenient().when(projectAccessGuard.requireSupervisorOwnsProject(supervisor, projectId))
                .thenReturn(project);
    }

    @Test
    void getJiraHierarchy_emptyProject_returnsEmptyRootsAndOrphans() {
        when(projectJiraIssueRepository.findAllByProjectId(projectId)).thenReturn(List.of());

        JiraHierarchyDto result = service.getJiraHierarchy(supervisorId.toString(), projectId.toString());

        assertThat(result.getRoots()).isEmpty();
        assertThat(result.getOrphans()).isEmpty();
    }

    @Test
    void getJiraHierarchy_flatEpicsOnly_allInRoots() {
        ProjectJiraIssue epic1 = issue("PRJ-1", "Epic", null);
        ProjectJiraIssue epic2 = issue("PRJ-2", "Epic", "");
        when(projectJiraIssueRepository.findAllByProjectId(projectId)).thenReturn(List.of(epic1, epic2));

        JiraHierarchyDto result = service.getJiraHierarchy(supervisorId.toString(), projectId.toString());

        assertThat(result.getRoots()).hasSize(2);
        assertThat(result.getRoots()).allSatisfy(node -> assertThat(node.getChildren()).isEmpty());
        assertThat(result.getOrphans()).isEmpty();
    }

    @Test
    void getJiraHierarchy_epicStorySubtask_buildsNestedTree() {
        ProjectJiraIssue epic = issue("PRJ-1", "Epic", null);
        ProjectJiraIssue story = issue("PRJ-2", "Story", "PRJ-1");
        ProjectJiraIssue subtask = issue("PRJ-3", "Subtask", "PRJ-2");
        when(projectJiraIssueRepository.findAllByProjectId(projectId)).thenReturn(List.of(epic, story, subtask));

        JiraHierarchyDto result = service.getJiraHierarchy(supervisorId.toString(), projectId.toString());

        assertThat(result.getRoots()).hasSize(1);
        JiraHierarchyDto.JiraHierarchyNodeDto root = result.getRoots().get(0);
        assertThat(root.getIssueKey()).isEqualTo("PRJ-1");
        assertThat(root.getChildren()).hasSize(1);
        assertThat(root.getChildren().get(0).getIssueKey()).isEqualTo("PRJ-2");
        assertThat(root.getChildren().get(0).getChildren()).hasSize(1);
        assertThat(root.getChildren().get(0).getChildren().get(0).getIssueKey()).isEqualTo("PRJ-3");
    }

    @Test
    void getJiraHierarchy_epicTaskSubtask_buildsNestedTree() {
        ProjectJiraIssue epic = issue("PRJ-1", "Epic", null);
        ProjectJiraIssue task = issue("PRJ-2", "Task", "PRJ-1");
        ProjectJiraIssue subtask = issue("PRJ-3", "Subtask", "PRJ-2");
        when(projectJiraIssueRepository.findAllByProjectId(projectId)).thenReturn(List.of(epic, task, subtask));

        JiraHierarchyDto result = service.getJiraHierarchy(supervisorId.toString(), projectId.toString());

        assertThat(result.getRoots()).hasSize(1);
        assertThat(result.getRoots().get(0).getChildren()).hasSize(1);
        assertThat(result.getRoots().get(0).getChildren().get(0).getIssueType()).isEqualTo("Task");
        assertThat(result.getRoots().get(0).getChildren().get(0).getChildren()).hasSize(1);
    }

    @Test
    void getJiraHierarchy_epicBugSubtask_buildsNestedTree() {
        ProjectJiraIssue epic = issue("PRJ-1", "Epic", null);
        ProjectJiraIssue bug = issue("PRJ-2", "Bug", "PRJ-1");
        ProjectJiraIssue subtask = issue("PRJ-3", "Subtask", "PRJ-2");
        when(projectJiraIssueRepository.findAllByProjectId(projectId)).thenReturn(List.of(epic, bug, subtask));

        JiraHierarchyDto result = service.getJiraHierarchy(supervisorId.toString(), projectId.toString());

        assertThat(result.getRoots()).hasSize(1);
        assertThat(result.getRoots().get(0).getChildren().get(0).getIssueType()).isEqualTo("Bug");
        assertThat(result.getRoots().get(0).getChildren().get(0).getChildren()).hasSize(1);
    }

    @Test
    void getJiraHierarchy_orphanParentKey_putsIssueInOrphans() {
        ProjectJiraIssue orphan = issue("PRJ-2", "Story", "PRJ-999");
        when(projectJiraIssueRepository.findAllByProjectId(projectId)).thenReturn(List.of(orphan));

        JiraHierarchyDto result = service.getJiraHierarchy(supervisorId.toString(), projectId.toString());

        assertThat(result.getRoots()).isEmpty();
        assertThat(result.getOrphans()).hasSize(1);
        assertThat(result.getOrphans().get(0).getIssueKey()).isEqualTo("PRJ-2");
    }

    @Test
    void getJiraHierarchy_mixedStructure_returnsExpectedCounts() {
        ProjectJiraIssue epic1 = issue("PRJ-1", "Epic", null);
        ProjectJiraIssue story = issue("PRJ-2", "Story", "PRJ-1");
        ProjectJiraIssue subtask = issue("PRJ-3", "Subtask", "PRJ-2");
        ProjectJiraIssue epic2 = issue("PRJ-4", "Epic", null);
        ProjectJiraIssue task = issue("PRJ-5", "Task", "PRJ-4");
        ProjectJiraIssue orphan = issue("PRJ-6", "Bug", "PRJ-999");
        when(projectJiraIssueRepository.findAllByProjectId(projectId))
                .thenReturn(List.of(epic1, story, subtask, epic2, task, orphan));

        JiraHierarchyDto result = service.getJiraHierarchy(supervisorId.toString(), projectId.toString());

        assertThat(result.getRoots()).hasSize(2);
        int totalDirectChildren = result.getRoots().stream().mapToInt(node -> node.getChildren().size()).sum();
        assertThat(totalDirectChildren).isEqualTo(2);
        assertThat(result.getOrphans()).hasSize(1);
    }

    private static ProjectJiraIssue issue(String issueKey, String issueType, String parentKey) {
        ProjectJiraIssue issue = new ProjectJiraIssue();
        issue.setIssueKey(issueKey);
        issue.setIssueType(issueType);
        issue.setSummary(issueKey + " summary");
        issue.setStatusName("To Do");
        issue.setPriorityName("Medium");
        issue.setParentKey(parentKey);
        return issue;
    }
}
