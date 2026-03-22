package com.supervisesuite.backend.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.memberships.entity.ProjectMember;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.ProjectService;
import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMilestoneRepository projectMilestoneRepository;

    @Mock
    private ProjectService projectService;

    private StudentServiceImpl service;

    private UUID studentId;
    private User student;

    @BeforeEach
    void setUp() {
        service = new StudentServiceImpl(
            userRepository,
            projectMemberRepository,
            projectRepository,
            projectMilestoneRepository,
            projectService
        );

        studentId = UUID.randomUUID();
        student = new User();
        student.setId(studentId);
        student.setRole(Roles.STUDENT);
        student.setEmail("student@university.ac.lk");
        student.setFirstName("Nimal");
        student.setLastName("Perera");
    }

    @Test
    void getProjects_noMemberships_returnsEmptyList() {
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(projectMemberRepository.findByUserIdAndMemberRoleOrderByCreatedAtDesc(studentId, Roles.STUDENT))
            .thenReturn(List.of());

        List<StudentProjectSummaryDto> result = service.getProjects(studentId.toString());

        assertThat(result).isEmpty();
    }

    @Test
    void getProjects_deduplicatesMembershipsAndMapsProjects() {
        UUID projectId = UUID.randomUUID();
        UUID projectId2 = UUID.randomUUID();

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(projectMemberRepository.findByUserIdAndMemberRoleOrderByCreatedAtDesc(studentId, Roles.STUDENT))
            .thenReturn(List.of(
                membership(studentId, projectId),
                membership(studentId, projectId),
                membership(studentId, projectId2)
            ));

        Project projectOne = project(projectId, "Project One");
        Project projectTwo = project(projectId2, "Project Two");
        User supervisor = new User();
        supervisor.setFirstName("Sup");
        supervisor.setLastName("One");
        supervisor.setEmail("sup@university.ac.lk");
        projectOne.setSupervisor(supervisor);
        projectTwo.setSupervisor(supervisor);

        when(projectRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anySet()))
            .thenReturn(List.of(projectOne, projectTwo));

        List<StudentProjectSummaryDto> result = service.getProjects(studentId.toString());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(StudentProjectSummaryDto::getTitle).containsExactly("Project One", "Project Two");
    }

    @Test
    void getProjectById_whenStudentNotMember_throwsEntityNotFound() {
        UUID projectId = UUID.randomUUID();

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(studentId, projectId, Roles.STUDENT))
            .thenReturn(false);

        assertThatThrownBy(() -> service.getProjectById(studentId.toString(), projectId.toString()))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getProjectGitHubActivityPage_withMembership_delegatesToProjectService() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId, "Project");
        ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> page =
            new ProjectGitHubPageDto<>(List.of(), 1, 10, 0, false);

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(studentId, projectId, Roles.STUDENT))
            .thenReturn(true);
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectService.getGitHubActivityPage(projectId, project.getRepositoryUrl(), 2, 25)).thenReturn(page);

        ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> result =
            service.getProjectGitHubActivityPage(studentId.toString(), projectId.toString(), 2, 25);

        assertThat(result).isSameAs(page);
        verify(projectService).getGitHubActivityPage(projectId, project.getRepositoryUrl(), 2, 25);
    }

    @Test
    void getProjects_invalidAuthenticatedUser_throwsUnauthorized() {
        assertThatThrownBy(() -> service.getProjects("not-a-uuid"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Authentication required");
    }

    private static ProjectMember membership(UUID userId, UUID projectId) {
        ProjectMember member = new ProjectMember();
        member.setId(UUID.randomUUID());
        member.setUserId(userId);
        member.setProjectId(projectId);
        member.setMemberRole(Roles.STUDENT);
        member.setCreatedAt(Instant.now());
        return member;
    }

    private static Project project(UUID projectId, String title) {
        Project project = new Project();
        project.setId(projectId);
        project.setName(title);
        project.setStatus("ACTIVE");
        project.setCreatedAt(Instant.now());
        project.setRepositoryUrl("https://github.com/acme/repo");
        return project;
    }
}
