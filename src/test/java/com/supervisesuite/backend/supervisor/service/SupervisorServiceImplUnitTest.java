package com.supervisesuite.backend.supervisor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallStartDto;
import com.supervisesuite.backend.projects.dto.LinkProjectGitHubRepositoryRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import com.supervisesuite.backend.projects.service.ProjectService;
import com.supervisesuite.backend.projects.service.githubv2.SetupCallbackService;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.service.githubv2.AccessSourceService;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMembersRequest;
import com.supervisesuite.backend.supervisor.dto.SupervisorDashboardDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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

    private SupervisorServiceImpl service;

    private UUID supervisorId;
    private User supervisor;

    @BeforeEach
    void setUp() {
        service = new SupervisorServiceImpl(
                userRepository,
                projectRepository,
                projectMemberRepository,
                projectMilestoneRepository,
                projectService,
                gitHubAppIntegrationService,
                setupCallbackService,
                repositoryLinkService,
                accessSourceService);

        supervisorId = UUID.randomUUID();
        supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setRole(Roles.SUPERVISOR);
        supervisor.setEmail("supervisor@university.ac.lk");
        supervisor.setFirstName("Sup");
        supervisor.setLastName("User");
        when(userRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));
    }

    @Test
    void getDashboard_computesProjectCounters() {
        Project planning = project("Planning", "PLANNING", LocalDate.now().plusDays(1));
        Project active = project("Active", "ACTIVE", LocalDate.now().plusDays(7));
        Project completed = project("Done", "COMPLETED", LocalDate.now().plusDays(20));

        when(projectRepository.findBySupervisorIdAndDeletedAtIsNullOrderByCreatedAtDesc(supervisorId))
                .thenReturn(List.of(planning, active, completed));

        SupervisorDashboardDto dashboard = service.getDashboard(supervisorId.toString());

        assertThat(dashboard.getTotalProjects()).isEqualTo(3);
        assertThat(dashboard.getPlanningProjects()).isEqualTo(1);
        assertThat(dashboard.getActiveProjects()).isEqualTo(1);
        assertThat(dashboard.getCompletedProjects()).isEqualTo(1);
        assertThat(dashboard.getUpcomingMilestonesCount()).isEqualTo(2);
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
        assertThat(project.getRepositoryUrl()).isEqualTo("https://github.com/acme/repo");
        verify(projectRepository).save(project);
    }

    @Test
    void removeProjectGitHubAccessAuthorization_clearsProjectRepositoryUrl() {
        UUID projectId = UUID.randomUUID();
        Project project = project("P1", "ACTIVE", LocalDate.now().plusDays(10));
        project.setId(projectId);
        project.setSupervisor(supervisor);
        project.setRepositoryUrl("https://github.com/acme/repo");

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
                .thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(projectService.getGitHubPreview(projectId, null))
                .thenReturn(new com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto(
                        false,
                        List.of(),
                        new com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto.ActivitySummary(0, null,
                                "idle"),
                        List.of(),
                        List.of()));
        when(projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of());
        when(projectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(projectId)).thenReturn(List.of());
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of());

        SupervisorProjectDetailDto result = service.removeProjectGitHubAccessAuthorization(supervisorId.toString(),
                projectId.toString());

        assertThat(result.getRepositoryUrl()).isNull();
        verify(projectService).clearGitHubLinkage(projectId);
    }

    @Test
    void createGitHubRepositoryAccessRequest_projectNotOwned_throwsNotFound() {
        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), supervisorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createGitHubRepositoryAccessRequest(
                supervisorId.toString(),
                "00000000-0000-0000-0000-000000000001")).isInstanceOf(EntityNotFoundException.class);
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
}
