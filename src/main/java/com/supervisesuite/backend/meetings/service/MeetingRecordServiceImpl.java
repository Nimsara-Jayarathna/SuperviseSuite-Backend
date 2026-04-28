package com.supervisesuite.backend.meetings.service;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.EntityIdParser;
import com.supervisesuite.backend.common.util.UserDisplayNameFormatter;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MeetingRecordServiceImpl implements MeetingRecordService {

    private static final int DISCUSSION_SUMMARY_MAX_LENGTH = 1024;
    private static final int DISCUSSION_DETAILS_MAX_LENGTH = 5000;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMeetingRecordRepository projectMeetingRecordRepository;
    private final ProjectMeetingChannelRepository projectMeetingChannelRepository;

    MeetingRecordServiceImpl(
        UserRepository userRepository,
        ProjectRepository projectRepository,
        ProjectMemberRepository projectMemberRepository,
        ProjectMeetingRecordRepository projectMeetingRecordRepository,
        ProjectMeetingChannelRepository projectMeetingChannelRepository
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectMeetingRecordRepository = projectMeetingRecordRepository;
        this.projectMeetingChannelRepository = projectMeetingChannelRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingRecordDto> listForSupervisor(String authenticatedUserId, String projectId) {
        User supervisor = resolveUser(authenticatedUserId, Roles.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        return projectMeetingRecordRepository.findByProjectIdPendingFirst(parsedProjectId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingRecordDto> listForStudent(String authenticatedUserId, String projectId) {
        User student = resolveUser(authenticatedUserId, Roles.STUDENT);
        UUID parsedProjectId = parseProjectId(projectId);

        boolean hasAccess = projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(
            student.getId(),
            parsedProjectId,
            Roles.STUDENT
        );
        if (!hasAccess) {
            throw new EntityNotFoundException();
        }

        return projectMeetingRecordRepository.findByProjectIdPendingFirst(parsedProjectId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional
    public MeetingRecordDto createAsSupervisor(
        String authenticatedUserId,
        String projectId,
        CreateMeetingRecordRequest request
    ) {
        User supervisor = resolveUser(authenticatedUserId, Roles.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        Project project = projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        Instant now = Instant.now();
        ProjectMeetingRecord record = new ProjectMeetingRecord();
        record.setProjectId(project.getId());
        record.setMeetingDate(validateMeetingDate(request.getMeetingDate()));
        record.setDurationMinutes(validateDurationMinutes(request.getDurationMinutes()));
        record.setDiscussionSummary(validateDiscussionSummary(request.getDiscussionSummary()));
        record.setDiscussionDetails(validateDiscussionDetails(request.getDiscussionDetails()));
        record.setChannelId(validateChannelId(request.getChannelId(), project.getId()));
        record.setAddedBy(supervisor.getId());
        record.setAddedByName(resolveUserDisplayName(supervisor));
        record.setAddedByRole(Roles.SUPERVISOR);
        record.setStatus(STATUS_APPROVED);
        record.setApprovedBy(supervisor.getId());
        record.setApprovedByName(resolveUserDisplayName(supervisor));
        record.setApprovedAt(now);

        ProjectMeetingRecord saved = projectMeetingRecordRepository.save(record);
        return toDto(saved);
    }

    @Override
    @Transactional
    public MeetingRecordDto createAsStudent(
        String authenticatedUserId,
        String projectId,
        CreateMeetingRecordRequest request
    ) {
        User student = resolveUser(authenticatedUserId, Roles.STUDENT);
        UUID parsedProjectId = parseProjectId(projectId);

        boolean hasAccess = projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(
            student.getId(),
            parsedProjectId,
            Roles.STUDENT
        );
        if (!hasAccess) {
            throw new EntityNotFoundException();
        }

        Project project = projectRepository
            .findByIdAndDeletedAtIsNull(parsedProjectId)
            .orElseThrow(EntityNotFoundException::new);

        ProjectMeetingRecord record = new ProjectMeetingRecord();
        record.setProjectId(project.getId());
        record.setMeetingDate(validateMeetingDate(request.getMeetingDate()));
        record.setDurationMinutes(validateDurationMinutes(request.getDurationMinutes()));
        record.setDiscussionSummary(validateDiscussionSummary(request.getDiscussionSummary()));
        record.setDiscussionDetails(validateDiscussionDetails(request.getDiscussionDetails()));
        record.setChannelId(validateChannelId(request.getChannelId(), project.getId()));
        record.setAddedBy(student.getId());
        record.setAddedByName(resolveUserDisplayName(student));
        record.setAddedByRole(Roles.STUDENT);
        record.setStatus(STATUS_PENDING);
        record.setApprovedBy(null);
        record.setApprovedByName(null);
        record.setApprovedAt(null);

        ProjectMeetingRecord saved = projectMeetingRecordRepository.save(record);
        return toDto(saved);
    }

    @Override
    @Transactional
    public MeetingRecordDto updateAsSupervisor(
        String authenticatedUserId,
        String projectId,
        String recordId,
        UpdateMeetingRecordRequest request
    ) {
        User supervisor = resolveUser(authenticatedUserId, Roles.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        UUID parsedRecordId = parseRecordId(recordId);
        ProjectMeetingRecord record = projectMeetingRecordRepository
            .findByIdAndProjectId(parsedRecordId, parsedProjectId)
            .orElseThrow(EntityNotFoundException::new);

        record.setMeetingDate(validateMeetingDate(request.getMeetingDate()));
        record.setDurationMinutes(validateDurationMinutes(request.getDurationMinutes()));
        record.setDiscussionSummary(validateDiscussionSummary(request.getDiscussionSummary()));
        record.setDiscussionDetails(validateDiscussionDetails(request.getDiscussionDetails()));
        record.setChannelId(validateChannelId(request.getChannelId(), parsedProjectId));
        ProjectMeetingRecord saved = projectMeetingRecordRepository.save(record);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void deleteAsSupervisor(String authenticatedUserId, String projectId, String recordId) {
        User supervisor = resolveUser(authenticatedUserId, Roles.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        UUID parsedRecordId = parseRecordId(recordId);
        ProjectMeetingRecord record = projectMeetingRecordRepository
            .findByIdAndProjectId(parsedRecordId, parsedProjectId)
            .orElseThrow(EntityNotFoundException::new);

        projectMeetingRecordRepository.delete(record);
    }

    @Override
    @Transactional
    public MeetingRecordDto approveAsSupervisor(String authenticatedUserId, String projectId, String recordId) {
        User supervisor = resolveUser(authenticatedUserId, Roles.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        UUID parsedRecordId = parseRecordId(recordId);
        ProjectMeetingRecord record = projectMeetingRecordRepository
            .findByIdAndProjectId(parsedRecordId, parsedProjectId)
            .orElseThrow(EntityNotFoundException::new);

        if (!STATUS_PENDING.equals(record.getStatus())) {
            throw new ValidationException("status", "Only pending meeting records can be approved.");
        }

        Instant now = Instant.now();
        record.setStatus(STATUS_APPROVED);
        record.setApprovedBy(supervisor.getId());
        record.setApprovedByName(resolveUserDisplayName(supervisor));
        record.setApprovedAt(now);

        ProjectMeetingRecord saved = projectMeetingRecordRepository.save(record);
        return toDto(saved);
    }

    private LocalDate validateMeetingDate(LocalDate value) {
        if (value == null) {
            throw new ValidationException("meetingDate", "Meeting date is required.");
        }
        return value;
    }

    private int validateDurationMinutes(Integer value) {
        if (value == null) {
            throw new ValidationException("durationMinutes", "Duration is required.");
        }
        if (value <= 0) {
            throw new ValidationException("durationMinutes", "Duration must be greater than 0 minutes.");
        }
        return value;
    }

    private String validateDiscussionSummary(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("discussionSummary", "Discussion summary is required.");
        }
        if (trimmed.length() > DISCUSSION_SUMMARY_MAX_LENGTH) {
            throw new ValidationException(
                "discussionSummary",
                "Discussion summary must be at most " + DISCUSSION_SUMMARY_MAX_LENGTH + " characters."
            );
        }
        return trimmed;
    }

    private String validateDiscussionDetails(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > DISCUSSION_DETAILS_MAX_LENGTH) {
            throw new ValidationException(
                "discussionDetails",
                "Discussion details must be at most " + DISCUSSION_DETAILS_MAX_LENGTH + " characters."
            );
        }
        return trimmed;
    }

    private UUID validateChannelId(UUID channelId, UUID projectId) {
        if (channelId == null) {
            return null;
        }

        ProjectMeetingChannel channel = projectMeetingChannelRepository.findById(channelId)
            .orElseThrow(() -> new ValidationException("channelId", "Invalid channel selected."));

        if (!projectId.equals(channel.getProjectId())) {
            throw new ValidationException("channelId", "Invalid channel selected.");
        }

        return channelId;
    }

    private MeetingRecordDto toDto(ProjectMeetingRecord record) {
        return new MeetingRecordDto(
            record.getId(),
            record.getProjectId(),
            record.getMeetingDate(),
            record.getDurationMinutes(),
            record.getDiscussionSummary(),
            record.getDiscussionDetails(),
            record.getChannelId(),
            record.getAddedBy(),
            record.getAddedByName(),
            record.getAddedByRole(),
            record.getStatus(),
            record.getApprovedBy(),
            record.getApprovedByName(),
            record.getApprovedAt(),
            record.getCreatedAt(),
            record.getUpdatedAt()
        );
    }

    private User resolveUser(String authenticatedUserId, String requiredRole) {
        UUID userId;
        try {
            userId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Authentication required.");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("Authentication required."));

        if (!requiredRole.equals(user.getRole())) {
            throw new UnauthorizedException("Authentication required.");
        }

        return user;
    }

    private String resolveUserDisplayName(User user) {
        return UserDisplayNameFormatter.format(user);
    }

    private UUID parseProjectId(String projectId) {
        return EntityIdParser.parseOrNotFound(projectId);
    }

    private UUID parseRecordId(String recordId) {
        return EntityIdParser.parseOrNotFound(recordId);
    }
}
