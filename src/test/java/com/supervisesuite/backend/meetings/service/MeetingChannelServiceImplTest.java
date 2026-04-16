package com.supervisesuite.backend.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.meetings.dto.CreateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.MeetingChannelDto;
import com.supervisesuite.backend.meetings.dto.UpdateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.entity.ProjectMeetingChannel;
import com.supervisesuite.backend.meetings.repository.ProjectMeetingChannelRepository;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
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
class MeetingChannelServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectMeetingChannelRepository projectMeetingChannelRepository;

    private MeetingChannelService service;

    @BeforeEach
    void setUp() {
        service = new MeetingChannelServiceImpl(
            userRepository,
            projectRepository,
            projectMemberRepository,
            projectMeetingChannelRepository
        );
    }

    @Test
    void listForSupervisor_requiresSupervisorAccess() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listForSupervisor(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void createAsStudent_setsPendingAndClearsApprovalFields() {
        UUID studentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User student = new User();
        student.setId(studentId);
        student.setRole(Roles.STUDENT);
        student.setEmail("student@example.com");

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(studentId, projectId, Roles.STUDENT))
            .thenReturn(true);

        Project project = new Project();
        project.setId(projectId);
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));

        when(projectMeetingChannelRepository.save(any(ProjectMeetingChannel.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateMeetingChannelRequest request = new CreateMeetingChannelRequest();
        request.setPlatform("zoom");
        request.setChannelName("  Weekly sync  ");
        request.setLinkOrIdentifier("  https://example.com  ");

        MeetingChannelDto dto = service.createAsStudent(studentId.toString(), projectId.toString(), request);

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.approvedBy()).isNull();
        assertThat(dto.approvedAt()).isNull();
        assertThat(dto.platform()).isEqualTo("ZOOM");
        assertThat(dto.channelName()).isEqualTo("Weekly sync");
        assertThat(dto.linkOrIdentifier()).isEqualTo("https://example.com");
    }

    @Test
    void createAsSupervisor_setsApprovedAndApprovalFields() {
        UUID supervisorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setRole(Roles.SUPERVISOR);
        supervisor.setEmail("supervisor@example.com");

        when(userRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));

        Project project = new Project();
        project.setId(projectId);
        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));

        when(projectMeetingChannelRepository.save(any(ProjectMeetingChannel.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateMeetingChannelRequest request = new CreateMeetingChannelRequest();
        request.setPlatform("TEAMS");
        request.setChannelName("Teams room");
        request.setLinkOrIdentifier("ABC123");

        MeetingChannelDto dto = service.createAsSupervisor(supervisorId.toString(), projectId.toString(), request);

        assertThat(dto.status()).isEqualTo("APPROVED");
        assertThat(dto.approvedBy()).isEqualTo(supervisorId);
        assertThat(dto.approvedAt()).isNotNull();
        assertThat(dto.addedByRole()).isEqualTo(Roles.SUPERVISOR);
    }

    @Test
    void approveAsSupervisor_transitionsPendingToApproved() {
        UUID supervisorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        User supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setRole(Roles.SUPERVISOR);
        supervisor.setEmail("supervisor@example.com");

        when(userRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));
        Project project = new Project();
        project.setId(projectId);
        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));

        ProjectMeetingChannel pending = new ProjectMeetingChannel();
        pending.setId(channelId);
        pending.setProjectId(projectId);
        pending.setStatus("PENDING");
        pending.setAddedBy(UUID.randomUUID());
        pending.setAddedByName("Student");
        pending.setAddedByRole(Roles.STUDENT);
        pending.setPlatform("ZOOM");
        pending.setChannelName("Weekly sync");
        pending.setLinkOrIdentifier("https://example.com");
        pending.setCreatedAt(Instant.now());

        when(projectMeetingChannelRepository.findByIdAndProjectId(channelId, projectId)).thenReturn(Optional.of(pending));
        when(projectMeetingChannelRepository.save(any(ProjectMeetingChannel.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        MeetingChannelDto dto = service.approveAsSupervisor(
            supervisorId.toString(),
            projectId.toString(),
            channelId.toString()
        );

        assertThat(dto.status()).isEqualTo("APPROVED");
        assertThat(dto.approvedBy()).isEqualTo(supervisorId);
        assertThat(dto.approvedAt()).isNotNull();
    }

    @Test
    void approveAsSupervisor_rejectsAlreadyApproved() {
        UUID supervisorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        User supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setRole(Roles.SUPERVISOR);
        supervisor.setEmail("supervisor@example.com");

        when(userRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));
        Project project = new Project();
        project.setId(projectId);
        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));

        ProjectMeetingChannel approved = new ProjectMeetingChannel();
        approved.setId(channelId);
        approved.setProjectId(projectId);
        approved.setStatus("APPROVED");
        when(projectMeetingChannelRepository.findByIdAndProjectId(channelId, projectId)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approveAsSupervisor(
            supervisorId.toString(),
            projectId.toString(),
            channelId.toString()
        )).isInstanceOf(ValidationException.class);
    }

    @Test
    void updateAsSupervisor_requiresProjectOwnership() {
        UUID supervisorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setRole(Roles.SUPERVISOR);
        supervisor.setEmail("supervisor@example.com");

        when(userRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));
        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId)).thenReturn(Optional.empty());

        UpdateMeetingChannelRequest request = new UpdateMeetingChannelRequest();
        request.setPlatform("ZOOM");
        request.setChannelName("Name");
        request.setLinkOrIdentifier("ID");

        assertThatThrownBy(() -> service.updateAsSupervisor(
            supervisorId.toString(),
            projectId.toString(),
            UUID.randomUUID().toString(),
            request
        )).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listForStudent_requiresMembership() {
        UUID studentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User student = new User();
        student.setId(studentId);
        student.setRole(Roles.STUDENT);
        student.setEmail("student@example.com");

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(studentId, projectId, Roles.STUDENT))
            .thenReturn(false);

        assertThatThrownBy(() -> service.listForStudent(studentId.toString(), projectId.toString()))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listForSupervisor_ordersByRepositoryRule() {
        UUID supervisorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setRole(Roles.SUPERVISOR);
        supervisor.setEmail("supervisor@example.com");

        when(userRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));
        Project project = new Project();
        project.setId(projectId);
        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));

        ProjectMeetingChannel pending = new ProjectMeetingChannel();
        pending.setId(UUID.randomUUID());
        pending.setProjectId(projectId);
        pending.setStatus("PENDING");
        pending.setPlatform("ZOOM");
        pending.setChannelName("Pending");
        pending.setLinkOrIdentifier("X");
        pending.setAddedBy(UUID.randomUUID());
        pending.setAddedByName("Student");
        pending.setAddedByRole(Roles.STUDENT);

        ProjectMeetingChannel approved = new ProjectMeetingChannel();
        approved.setId(UUID.randomUUID());
        approved.setProjectId(projectId);
        approved.setStatus("APPROVED");
        approved.setPlatform("ZOOM");
        approved.setChannelName("Approved");
        approved.setLinkOrIdentifier("Y");
        approved.setAddedBy(UUID.randomUUID());
        approved.setAddedByName("Supervisor");
        approved.setAddedByRole(Roles.SUPERVISOR);

        when(projectMeetingChannelRepository.findByProjectIdPendingFirst(projectId)).thenReturn(List.of(pending, approved));

        List<MeetingChannelDto> result = service.listForSupervisor(supervisorId.toString(), projectId.toString());

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().status()).isEqualTo("PENDING");
        assertThat(result.get(1).status()).isEqualTo("APPROVED");
        verify(projectMeetingChannelRepository).findByProjectIdPendingFirst(eq(projectId));
    }
}

