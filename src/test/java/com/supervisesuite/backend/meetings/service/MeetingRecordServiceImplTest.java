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
import com.supervisesuite.backend.meetings.dto.CreateMeetingRecordRequest;
import com.supervisesuite.backend.meetings.dto.MeetingRecordDto;
import com.supervisesuite.backend.meetings.dto.UpdateMeetingRecordRequest;
import com.supervisesuite.backend.meetings.entity.ProjectMeetingChannel;
import com.supervisesuite.backend.meetings.entity.ProjectMeetingRecord;
import com.supervisesuite.backend.meetings.repository.ProjectMeetingChannelRepository;
import com.supervisesuite.backend.meetings.repository.ProjectMeetingRecordRepository;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
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
class MeetingRecordServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectMeetingRecordRepository projectMeetingRecordRepository;

    @Mock
    private ProjectMeetingChannelRepository projectMeetingChannelRepository;

    private MeetingRecordService service;

    @BeforeEach
    void setUp() {
        service = new MeetingRecordServiceImpl(
            userRepository,
            projectRepository,
            projectMemberRepository,
            projectMeetingRecordRepository,
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
    void createAsStudent_setsPendingAndClearsApprovalFields_andTrimsInput() {
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

        when(projectMeetingRecordRepository.save(any(ProjectMeetingRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateMeetingRecordRequest request = new CreateMeetingRecordRequest();
        request.setMeetingDate(LocalDate.parse("2026-04-18"));
        request.setDurationMinutes(45);
        request.setDiscussionSummary("  Weekly sync  ");
        request.setDiscussionDetails("  Notes  ");
        request.setChannelId(null);

        MeetingRecordDto dto = service.createAsStudent(studentId.toString(), projectId.toString(), request);

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.approvedBy()).isNull();
        assertThat(dto.approvedAt()).isNull();
        assertThat(dto.discussionSummary()).isEqualTo("Weekly sync");
        assertThat(dto.discussionDetails()).isEqualTo("Notes");
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

        when(projectMeetingRecordRepository.save(any(ProjectMeetingRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateMeetingRecordRequest request = new CreateMeetingRecordRequest();
        request.setMeetingDate(LocalDate.parse("2026-04-18"));
        request.setDurationMinutes(60);
        request.setDiscussionSummary("Summary");
        request.setDiscussionDetails(null);
        request.setChannelId(null);

        MeetingRecordDto dto = service.createAsSupervisor(supervisorId.toString(), projectId.toString(), request);

        assertThat(dto.status()).isEqualTo("APPROVED");
        assertThat(dto.approvedBy()).isEqualTo(supervisorId);
        assertThat(dto.approvedAt()).isNotNull();
    }

    @Test
    void approveAsSupervisor_rejectsAlreadyApproved() {
        UUID supervisorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        User supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setRole(Roles.SUPERVISOR);
        supervisor.setEmail("supervisor@example.com");

        when(userRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));
        Project project = new Project();
        project.setId(projectId);
        when(projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisorId))
            .thenReturn(Optional.of(project));

        ProjectMeetingRecord approved = new ProjectMeetingRecord();
        approved.setId(recordId);
        approved.setProjectId(projectId);
        approved.setStatus("APPROVED");
        when(projectMeetingRecordRepository.findByIdAndProjectId(recordId, projectId)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approveAsSupervisor(
            supervisorId.toString(),
            projectId.toString(),
            recordId.toString()
        )).isInstanceOf(ValidationException.class);
    }

    @Test
    void createAsStudent_rejectsUnknownChannelId() {
        UUID studentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
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

        when(projectMeetingChannelRepository.findById(channelId)).thenReturn(Optional.empty());

        CreateMeetingRecordRequest request = new CreateMeetingRecordRequest();
        request.setMeetingDate(LocalDate.parse("2026-04-18"));
        request.setDurationMinutes(30);
        request.setDiscussionSummary("Summary");
        request.setDiscussionDetails(null);
        request.setChannelId(channelId);

        assertThatThrownBy(() -> service.createAsStudent(studentId.toString(), projectId.toString(), request))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void createAsStudent_rejectsChannelFromAnotherProject() {
        UUID studentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
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

        ProjectMeetingChannel channel = new ProjectMeetingChannel();
        channel.setId(channelId);
        channel.setProjectId(otherProjectId);
        when(projectMeetingChannelRepository.findById(channelId)).thenReturn(Optional.of(channel));

        CreateMeetingRecordRequest request = new CreateMeetingRecordRequest();
        request.setMeetingDate(LocalDate.parse("2026-04-18"));
        request.setDurationMinutes(30);
        request.setDiscussionSummary("Summary");
        request.setDiscussionDetails(null);
        request.setChannelId(channelId);

        assertThatThrownBy(() -> service.createAsStudent(studentId.toString(), projectId.toString(), request))
            .isInstanceOf(ValidationException.class);
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

        when(projectMeetingRecordRepository.findByProjectIdPendingFirst(projectId)).thenReturn(List.of());

        service.listForSupervisor(supervisorId.toString(), projectId.toString());

        verify(projectMeetingRecordRepository).findByProjectIdPendingFirst(eq(projectId));
    }
}

