package com.supervisesuite.backend.common.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectAccessGuardImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;

    private ProjectAccessGuardImpl guard;

    @BeforeEach
    void setUp() {
        guard = new ProjectAccessGuardImpl(userRepository, projectRepository, projectMemberRepository);
    }

    @Test
    void requireSupervisor_invalidUuid_throwsUnauthorized() {
        assertThatThrownBy(() -> guard.requireSupervisor("not-a-uuid"))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void requireSupervisor_wrongRole_throwsUnauthorized() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setRole(Roles.STUDENT);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> guard.requireSupervisor(id.toString()))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void requireSupervisorOwnsProject_missing_throwsEntityNotFound() {
        UUID supervisorId = UUID.randomUUID();
        User supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setRole(Roles.SUPERVISOR);
        UUID projectId = UUID.randomUUID();

        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireSupervisorOwnsProject(supervisor, projectId))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void requireStudentIsMember_notMember_throwsEntityNotFound() {
        UUID studentId = UUID.randomUUID();
        User student = new User();
        student.setId(studentId);
        student.setRole(Roles.STUDENT);
        UUID projectId = UUID.randomUUID();

        when(projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(studentId, projectId, Roles.STUDENT))
            .thenReturn(false);

        assertThatThrownBy(() -> guard.requireStudentIsMember(student, projectId))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void requireStudentIsMember_member_returnsProject() {
        UUID studentId = UUID.randomUUID();
        User student = new User();
        student.setId(studentId);
        student.setRole(Roles.STUDENT);
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);

        when(projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(studentId, projectId, Roles.STUDENT))
            .thenReturn(true);
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
            .thenReturn(Optional.of(project));

        assertThat(guard.requireStudentIsMember(student, projectId)).isSameAs(project);
    }
}

