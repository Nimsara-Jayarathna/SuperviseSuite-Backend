package com.supervisesuite.backend.supervisor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallStartDto;
import com.supervisesuite.backend.projects.dto.JiraAuthUrlDto;
import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.dto.LinkProjectGitHubRepositoryRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteRequestDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.entity.ProjectJiraOAuthState;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraOAuthStateRepository;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import com.supervisesuite.backend.projects.service.ProjectService;
import com.supervisesuite.backend.projects.service.githubv2.SetupCallbackService;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.service.githubv2.AccessSourceService;
import com.supervisesuite.backend.projects.service.githubv2.AccessRequestService;
import com.supervisesuite.backend.projects.service.jira.JiraHealthService;
import com.supervisesuite.backend.projects.service.jira.JiraIssueSyncService;
import com.supervisesuite.backend.projects.service.jira.JiraSprintProgressService;
import com.supervisesuite.backend.projects.service.jira.JiraTokenEncryptionService;
import com.supervisesuite.backend.projects.service.jira.JiraWorkloadService;
import com.supervisesuite.backend.projects.service.milestones.MilestonePolicyEngine;
import com.supervisesuite.backend.projects.service.milestones.ProjectMilestoneAggregateService;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileListDto;
import com.supervisesuite.backend.projectfiles.service.ProjectFileService;
import com.supervisesuite.backend.meetings.service.MeetingChannelService;
import com.supervisesuite.backend.meetings.service.MeetingRecordService;
import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMembersRequest;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.SupervisorDashboardDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupervisorServiceImplUnitTest {

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
    private User supervisor;

    @BeforeEach
    void setUp() {
        MilestonePolicyEngine milestonePolicyEngine = new MilestonePolicyEngine();
        ProjectMilestoneAggregateService projectMilestoneAggregateService =
                new ProjectMilestoneAggregateService(projectMilestoneRepository, milestonePolicyEngine);
        service = new SupervisorServiceImpl(
                userRepository,
                projectRepository,
                projectMemberRepository,
                projectMilestoneRepository,
                projectService,
                gitHubAppIntegrationService,
                setupCallbackService,
                repositoryLinkService,
                accessSourceService,
                accessRequestService,
                jiraProperties,
                projectJiraIntegrationRepository,
                projectJiraOAuthStateRepository,
                jiraTokenEncryptionService,
                projectJiraIssueRepository,
                jiraIssueSyncService,
                jiraHealthService,
                jiraSprintProgressService,
                jiraWorkloadService,
                projectFileService,
                meetingChannelService,
                meetingRecordService,
                restClientBuilder,
                milestonePolicyEngine,
                projectMilestoneAggregateService,
                projectAccessGuard);

        supervisorId = UUID.randomUUID();
        supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setRole(Roles.SUPERVISOR);
        supervisor.setEmail("supervisor@university.ac.lk");
        supervisor.setFirstName("Sup");
        supervisor.setLastName("User");
        lenient().when(projectAccessGuard.requireSupervisor(supervisorId.toString())).thenReturn(supervisor);
        lenient().when(projectAccessGuard.requireSupervisorOwnsProject(
                org.mockito.ArgumentMatchers.eq(supervisor),
                org.mockito.ArgumentMatchers.any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID projectId = invocation.getArgument(1);
                Project project = new Project();
                project.setId(projectId);
                project.setSupervisor(supervisor);
                project.setName("Project");
                project.setDescription("Description");
                project.setBatch("2025");
                project.setSemester("Y3S2");
                project.setStatus("PLANNING");
                project.setProgressPercent(0);
                project.setCreatedAt(Instant.now());
                project.setLastActivityAt(Instant.now());
                return project;
            });
        lenient().when(restClientBuilder.build()).thenReturn(restClient);
    }

    @Test
    void getDashboard_computesProjectCounters() {
        Project planning = project("Planning", "PLANNING", LocalDate.now().plusDays(1));
        Project active = project("Active", "ACTIVE", LocalDate.now().plusDays(7));
        Project completed = project("Done", "COMPLETED", LocalDate.now().plusDays(20));

        when(projectRepository.findBySupervisorIdAndDeletedAtIsNullOrderByCreatedAtDesc(supervisorId))
                .thenReturn(List.of(planning, active, completed));
        when(projectJiraIntegrationRepository.findAllByProjectIdInAndRevokedAtIsNull(ArgumentMatchers.anyList()))
                .thenReturn(List.of());

        SupervisorDashboardDto dashboard = service.getDashboard(supervisorId.toString());

        assertThat(dashboard.getTotalProjects()).isEqualTo(3);
        assertThat(dashboard.getPlanningProjects()).isEqualTo(1);
        assertThat(dashboard.getActiveProjects()).isEqualTo(1);
        assertThat(dashboard.getCompletedProjects()).isEqualTo(1);
        assertThat(dashboard.getUpcomingMilestonesCount()).isEqualTo(2);
        verify(projectMilestoneRepository, never())
                .findByProjectIdOrderBySequenceNoAsc(ArgumentMatchers.any(UUID.class));
    }

    @Test
    void addProjectMembers_existingMember_throwsValidationException() {
        UUID projectId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        AddSupervisorProjectMembersRequest request = new AddSupervisorProjectMembersRequest();
        request.setStudentIds(List.of(studentId));

        User student = new User();
        student.setId(studentId);
        student.setRole(Roles.STUDENT);

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(student));
        when(projectMemberRepository.existsByUserIdAndProjectId(studentId, projectId)).thenReturn(true);

        assertThatThrownBy(() -> service.addProjectMembers(supervisorId.toString(), projectId.toString(), request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateProjectMilestone_invalidStatus_throwsValidationException() {
        UUID projectId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();

        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setId(milestoneId);
        milestone.setProjectId(projectId);
        milestone.setTitle("M1");
        milestone.setStatus("PLANNED");

        UpdateSupervisorProjectMilestoneRequest request = new UpdateSupervisorProjectMilestoneRequest();
        request.setTitle("M1");
        request.setDueDate(LocalDate.now().plusDays(3));
        request.setStatus("UNKNOWN");

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(projectMilestoneRepository.findByIdAndProjectId(milestoneId, projectId))
                .thenReturn(Optional.of(milestone));

        assertThatThrownBy(() -> service.updateProjectMilestone(
                supervisorId.toString(),
                projectId.toString(),
                milestoneId.toString(),
                request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Validation failed");
    }

    @Test
    void addProjectMilestone_openPastDueDate_throwsValidationException() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        AddSupervisorProjectMilestoneRequest request = new AddSupervisorProjectMilestoneRequest();
        request.setTitle("Late milestone");
        request.setDueDate(LocalDate.now().minusDays(1));

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(projectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(projectId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.addProjectMilestone(supervisorId.toString(), projectId.toString(), request))
                .isInstanceOfSatisfying(ValidationException.class, exception -> {
                    assertThat(exception.getDetails()).hasSize(1);
                    assertThat(exception.getDetails().getFirst().getField()).isEqualTo("dueDate");
                });
    }

    @Test
    void updateProjectMilestone_completedToPlanned_throwsValidationException() {
        UUID projectId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();

        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setId(milestoneId);
        milestone.setProjectId(projectId);
        milestone.setTitle("M1");
        milestone.setStatus("COMPLETED");
        milestone.setDueDate(LocalDate.now().plusDays(3));

        UpdateSupervisorProjectMilestoneRequest request = new UpdateSupervisorProjectMilestoneRequest();
        request.setTitle("M1");
        request.setDueDate(milestone.getDueDate());
        request.setStatus("PLANNED");

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(projectMilestoneRepository.findByIdAndProjectId(milestoneId, projectId))
                .thenReturn(Optional.of(milestone));

        assertThatThrownBy(() -> service.updateProjectMilestone(
                supervisorId.toString(),
                projectId.toString(),
                milestoneId.toString(),
                request))
                .isInstanceOfSatisfying(ValidationException.class, exception -> {
                    assertThat(exception.getDetails()).hasSize(1);
                    assertThat(exception.getDetails().getFirst().getField()).isEqualTo("status");
                });
    }

    @Test
    void updateProjectMilestone_legacyInvalidDueDateChanged_keepsRuleEnforcement() {
        UUID projectId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();

        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setId(milestoneId);
        milestone.setProjectId(projectId);
        milestone.setTitle("Legacy");
        milestone.setStatus("PLANNED");
        milestone.setDueDate(LocalDate.now().minusDays(1));

        UpdateSupervisorProjectMilestoneRequest request = new UpdateSupervisorProjectMilestoneRequest();
        request.setTitle("Legacy updated");
        request.setDueDate(LocalDate.now().minusDays(2));
        request.setStatus("PLANNED");

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(projectMilestoneRepository.findByIdAndProjectId(milestoneId, projectId))
                .thenReturn(Optional.of(milestone));

        assertThatThrownBy(() -> service.updateProjectMilestone(
                supervisorId.toString(),
                projectId.toString(),
                milestoneId.toString(),
                request))
                .isInstanceOfSatisfying(ValidationException.class, exception -> {
                    assertThat(exception.getDetails()).hasSize(1);
                    assertThat(exception.getDetails().getFirst().getField()).isEqualTo("dueDate");
                });
    }

    @Test
    void addProjectMilestone_recomputesProjectMilestoneDateFromAllMilestones() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(30));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        ProjectMilestone existing = new ProjectMilestone();
        existing.setId(UUID.randomUUID());
        existing.setProjectId(projectId);
        existing.setSequenceNo(1);
        existing.setStatus("PLANNED");
        existing.setDueDate(LocalDate.now().plusDays(2));

        ProjectMilestone created = new ProjectMilestone();
        created.setId(UUID.randomUUID());
        created.setProjectId(projectId);
        created.setSequenceNo(2);
        created.setStatus("PLANNED");
        created.setDueDate(LocalDate.now().plusDays(10));

        AddSupervisorProjectMilestoneRequest request = new AddSupervisorProjectMilestoneRequest();
        request.setTitle("M2");
        request.setDueDate(created.getDueDate());

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(projectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(projectId))
                .thenReturn(List.of(existing), List.of(existing, created), List.of(existing, created));
        when(projectMilestoneRepository.save(ArgumentMatchers.any(ProjectMilestone.class))).thenReturn(created);
        when(projectRepository.save(project)).thenReturn(project);
        when(repositoryLinkService.resolveLink(projectId)).thenReturn(null);
        when(projectService.getGitHubPreview(projectId, null)).thenReturn(emptyGitHubPreview());
        when(projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of());
        when(userRepository.findAllById(ArgumentMatchers.anyCollection())).thenReturn(List.of());
        when(projectJiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId))
                .thenReturn(Optional.empty());

        SupervisorProjectDetailDto result = service.addProjectMilestone(
                supervisorId.toString(),
                projectId.toString(),
                request);

        assertThat(result.getMilestoneDate()).isEqualTo(existing.getDueDate());
    }

    @Test
    void getProjectById_legacyInvalidMilestones_areFlaggedInResponse() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        ProjectMilestone ordered = new ProjectMilestone();
        ordered.setId(UUID.randomUUID());
        ordered.setProjectId(projectId);
        ordered.setSequenceNo(1);
        ordered.setTitle("M1");
        ordered.setStatus("PLANNED");
        ordered.setDueDate(LocalDate.now().plusDays(1));

        ProjectMilestone legacyInvalid = new ProjectMilestone();
        legacyInvalid.setId(UUID.randomUUID());
        legacyInvalid.setProjectId(projectId);
        legacyInvalid.setSequenceNo(2);
        legacyInvalid.setTitle("M2");
        legacyInvalid.setStatus("PLANNED");
        legacyInvalid.setDueDate(LocalDate.now().minusDays(1));

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(projectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(projectId))
                .thenReturn(List.of(ordered, legacyInvalid));
        when(repositoryLinkService.resolveLink(projectId)).thenReturn(null);
        when(projectService.getGitHubPreview(projectId, null)).thenReturn(emptyGitHubPreview());
        when(projectJiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId))
                .thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of());
        when(userRepository.findAllById(ArgumentMatchers.anyCollection())).thenReturn(List.of());
        when(projectFileService.listFiles(
                supervisorId.toString(),
                projectId.toString(),
                com.supervisesuite.backend.projectfiles.service.ProjectFileAccessRole.SUPERVISOR))
                .thenReturn(new ProjectFileListDto(
                        List.of(),
                        new ProjectFileListDto.Config(10_000_000L, 255, List.of("pdf"), 300)));

        SupervisorProjectDetailDto detail = service.getProjectById(supervisorId.toString(), projectId.toString());

        assertThat(detail.getMilestones()).hasSize(2);
        SupervisorProjectDetailDto.Milestone legacyMilestone = detail.getMilestones().get(1);
        assertThat(legacyMilestone.getIsOverdue()).isTrue();
        assertThat(legacyMilestone.getDaysOverdue()).isGreaterThan(0);
        assertThat(legacyMilestone.getIsChronologyViolation()).isTrue();
        assertThat(detail.getMilestoneInsights()).isNotNull();
        assertThat(detail.getMilestoneInsights().getOverdueOpenMilestones()).isEqualTo(1);
        assertThat(detail.getMilestoneInsights().getTimelineRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void createGitHubRepositoryAccessRequest_delegatesToIntegrationService() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        GitHubAccessRequestCreateDto dto = new GitHubAccessRequestCreateDto(projectId, "token",
                "/github/request-access", Instant.now());

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(gitHubAppIntegrationService.createProjectAccessRequest(projectId, supervisorId)).thenReturn(dto);

        GitHubAccessRequestCreateDto result = service.createGitHubRepositoryAccessRequest(supervisorId.toString(),
                projectId.toString());

        assertThat(result).isSameAs(dto);
        verify(gitHubAppIntegrationService).createProjectAccessRequest(projectId, supervisorId);
    }

    @Test
    void buildGitHubSetupStartUrl_delegatesToIntegrationServiceAfterOwnershipCheck() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        String authorizeUrl = "https://github.com/apps/supervisesuite/installations/new?state=test";
        GitHubInstallStartDto setup = new GitHubInstallStartDto(
                projectId.toString(),
                authorizeUrl,
                "INSTALLATION_DIRECT",
                Instant.now().plusSeconds(600));

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(setupCallbackService.startDirectInstall(projectId.toString(), supervisorId.toString()))
                .thenReturn(setup);

        String result = service.buildGitHubSetupStartUrl(supervisorId.toString(), projectId.toString());

        assertThat(result).isEqualTo(authorizeUrl);
        verify(setupCallbackService).startDirectInstall(projectId.toString(), supervisorId.toString());
    }

    @Test
    void linkProjectGitHubRepository_updatesProjectRepositoryUrl() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        LinkProjectGitHubRepositoryRequest request = new LinkProjectGitHubRepositoryRequest();
        request.setInstallationId(10L);
        request.setRepositoryId(20L);

        ProjectGitHubRepositoryLinkDto linked = new ProjectGitHubRepositoryLinkDto(
                projectId,
                10L,
                20L,
                "repo",
                "acme/repo",
                "https://github.com/acme/repo",
                "acme",
                "main",
                Instant.now());

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(projectService.linkProjectToInstallationRepository(projectId, 10L, 20L, supervisorId)).thenReturn(linked);
        when(projectRepository.save(project)).thenReturn(project);

        ProjectGitHubRepositoryLinkDto result = service.linkProjectGitHubRepository(supervisorId.toString(),
                projectId.toString(), request);

        assertThat(result).isSameAs(linked);
        verify(projectService).linkProjectToInstallationRepository(projectId, 10L, 20L, supervisorId);
    }

    @Test
    void removeProjectGitHubAccessAuthorization_clearsProjectRepositoryUrl() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto preview = new com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto(
                        false,
                        List.of(),
                        new com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto.ActivitySummary(0, null,
                                "idle"),
                        List.of(),
                        List.of());
        preview.setRepositoryUrl(null);
        when(projectService.getGitHubPreview(projectId, null))
                .thenReturn(preview);
        when(projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of());
        when(projectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(projectId)).thenReturn(List.of());
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of());

        SupervisorProjectDetailDto result = service.removeProjectGitHubAccessAuthorization(supervisorId.toString(),
                projectId.toString());

        assertThat(result.getGithub().getRepositoryUrl()).isNull();
        verify(repositoryLinkService).disconnectAllLinks(projectId);
    }

    @Test
    void createGitHubRepositoryAccessRequest_projectNotOwned_throwsNotFound() {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(projectAccessGuard.requireSupervisorOwnsProject(supervisor, projectId))
            .thenThrow(new EntityNotFoundException());

        assertThatThrownBy(() -> service.createGitHubRepositoryAccessRequest(
                supervisorId.toString(),
                projectId.toString())).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getProjectJiraAuthUrl_whenAlreadyConnected_throwsValidationException() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        ProjectJiraIntegration integration = new ProjectJiraIntegration();
        integration.setProjectId(projectId);
        integration.setWorkspaceName("supervise-suite");

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));
        when(projectJiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId))
            .thenReturn(Optional.of(integration));

        assertThatThrownBy(() -> service.getProjectJiraAuthUrl(supervisorId.toString(), projectId.toString()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("already connected");
    }

    @Test
    void completeJiraOAuth_whenAccessDenied_throwsFriendlyValidationMessage() {
        JiraOAuthCompleteRequestDto request = new JiraOAuthCompleteRequestDto();
        request.setError("access_denied");
        request.setErrorDescription("User did not authorize the request");

        assertThatThrownBy(() -> service.completeJiraOAuth(supervisorId.toString(), request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("cancelled");
    }

    @Test
    void getProjectJiraAuthUrl_persistsOAuthStateNonceAndReturnsAuthUrl() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));
        when(projectJiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId))
            .thenReturn(Optional.empty());
        when(jiraProperties.getClientId()).thenReturn("client-id");
        when(jiraProperties.getRedirectUri()).thenReturn("http://localhost:5173/supervisor/jira/callback");
        when(jiraProperties.getAuthTargetUrl()).thenReturn("https://auth.atlassian.com/authorize");
        when(jiraProperties.getAudience()).thenReturn("api.atlassian.com");
        when(jiraProperties.getScope()).thenReturn("read:jira-user read:jira-work");
        when(jiraProperties.getOauthStateTtlSeconds()).thenReturn(900L);

        JiraAuthUrlDto result = service.getProjectJiraAuthUrl(supervisorId.toString(), projectId.toString());
        String decodedUrl = decodeUrl(result.url());

        assertThat(result.url()).contains("https://auth.atlassian.com/authorize");
        assertThat(result.url()).contains("state=");
        assertThat(decodedUrl).contains("scope=read:jira-user read:jira-work offline_access");

        ArgumentCaptor<ProjectJiraOAuthState> captor = ArgumentCaptor.forClass(ProjectJiraOAuthState.class);
        verify(projectJiraOAuthStateRepository).saveAndFlush(captor.capture());
        ProjectJiraOAuthState saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(projectId);
        assertThat(saved.getUserId()).isEqualTo(supervisorId);
        assertThat(saved.getStateNonceHash()).isNotBlank();
        assertThat(saved.getExpiresAt()).isNotNull();
    }

    @Test
    void getProjectJiraAuthUrl_whenOfflineAccessAlreadyPresent_doesNotDuplicateScope() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));
        when(projectJiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId))
            .thenReturn(Optional.empty());
        when(jiraProperties.getClientId()).thenReturn("client-id");
        when(jiraProperties.getRedirectUri()).thenReturn("http://localhost:5173/supervisor/jira/callback");
        when(jiraProperties.getAuthTargetUrl()).thenReturn("https://auth.atlassian.com/authorize");
        when(jiraProperties.getAudience()).thenReturn("api.atlassian.com");
        when(jiraProperties.getScope()).thenReturn("read:jira-user read:jira-work offline_access");
        when(jiraProperties.getOauthStateTtlSeconds()).thenReturn(900L);

        JiraAuthUrlDto result = service.getProjectJiraAuthUrl(supervisorId.toString(), projectId.toString());
        String decodedUrl = decodeUrl(result.url());

        assertThat(decodedUrl).contains("scope=read:jira-user read:jira-work offline_access");
        assertThat(decodedUrl.split("offline_access", -1)).hasSize(2);
    }

    @Test
    void completeJiraOAuth_withInvalidState_throwsValidationAndDoesNotCallAtlassian() {
        JiraOAuthCompleteRequestDto request = new JiraOAuthCompleteRequestDto();
        request.setCode("oauth-code");
        request.setState("bad-state-without-colon");

        assertThatThrownBy(() -> service.completeJiraOAuth(supervisorId.toString(), request))
            .isInstanceOfSatisfying(ValidationException.class, exception -> {
                assertThat(exception.getMessage()).isEqualTo("Validation failed.");
                assertThat(exception.getDetails()).hasSize(1);
                assertThat(exception.getDetails().getFirst().getField()).isEqualTo("state");
                assertThat(exception.getDetails().getFirst().getIssue())
                    .contains("OAuth state format is invalid");
            });

        verify(restClient, never()).post();
    }

    @Test
    void disconnectProjectJira_whenActiveIntegrationExists_deletesIntegration() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        ProjectJiraIntegration integration = new ProjectJiraIntegration();
        integration.setProjectId(projectId);
        integration.setWorkspaceName("supervise-suite");
        integration.setWorkspaceUrl("https://example.atlassian.net");

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));
        when(projectJiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId))
            .thenReturn(Optional.of(integration))
            .thenReturn(Optional.empty());
        when(projectRepository.save(project)).thenReturn(project);

        com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto preview =
            new com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto(
                false,
                List.of(),
                new com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto.ActivitySummary(0, null, "idle"),
                List.of(),
                List.of());
        when(projectService.getGitHubPreview(projectId, null)).thenReturn(preview);
        when(projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of());
        when(projectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(projectId)).thenReturn(List.of());
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of());

        SupervisorProjectDetailDto result =
            service.disconnectProjectJira(supervisorId.toString(), projectId.toString());

        assertThat(result.getJira()).isNotNull();
        assertThat(result.getJira().isConnected()).isFalse();
        verify(projectJiraIntegrationRepository).delete(integration);
        verify(projectJiraIssueRepository).deleteAllByProjectId(projectId);
    }

    @Test
    void getJiraHealthOverview_whenOwnedProject_delegatesToHealthService() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        JiraHealthDto health = new JiraHealthDto(
            90.0,
            2,
            1,
            1,
            new JiraHealthDto.StatusBreakdown(1, 1, 18),
            List.of(),
            50.0,
            Instant.now()
        );

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));
        when(jiraHealthService.getHealthOverview(projectId)).thenReturn(health);

        JiraHealthDto result = service.getJiraHealthOverview(supervisorId.toString(), projectId.toString());

        assertThat(result).isSameAs(health);
        verify(jiraHealthService).getHealthOverview(projectId);
    }

    @Test
    void refreshProjectJiraData_whenConnected_syncsAndReturnsHealthOverview() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        ProjectJiraIntegration integration = new ProjectJiraIntegration();
        integration.setProjectId(projectId);

        JiraHealthDto health = new JiraHealthDto(
            70.0,
            6,
            2,
            2,
            new JiraHealthDto.StatusBreakdown(2, 4, 14),
            List.of(),
            33.3,
            Instant.now()
        );

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));
        when(projectJiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId))
            .thenReturn(Optional.of(integration));
        when(projectRepository.save(project)).thenReturn(project);
        when(jiraHealthService.getHealthOverview(projectId)).thenReturn(health);

        JiraHealthDto result = service.refreshProjectJiraData(supervisorId.toString(), projectId.toString());

        assertThat(result).isSameAs(health);
        verify(jiraIssueSyncService).syncProjectIssues(projectId);
        verify(jiraHealthService).getHealthOverview(projectId);
    }

    @Test
    void refreshProjectJiraData_whenJiraNotConnected_throwsValidationException() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));
        when(projectJiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshProjectJiraData(supervisorId.toString(), projectId.toString()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Jira is not connected");

        verify(jiraIssueSyncService, never()).syncProjectIssues(projectId);
    }

    private static Project project(String title, String status, LocalDate milestoneDate) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName(title);
        project.setStatus(status);
        project.setMilestoneDate(milestoneDate);
        project.setCreatedAt(Instant.now());
        project.setLastActivityAt(Instant.now());
        project.setProgressPercent(0);
        return project;
    }

    private static com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto emptyGitHubPreview() {
        return new com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto(
                false,
                List.of(),
                new com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto.ActivitySummary(0, null, "idle"),
                List.of(),
                List.of());
    }

    private static String decodeUrl(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
