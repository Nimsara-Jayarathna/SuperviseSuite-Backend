package com.supervisesuite.backend.meetings.service;

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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MeetingChannelServiceImpl implements MeetingChannelService {

    private static final int CHANNEL_NAME_MAX_LENGTH = 120;
    private static final int LINK_OR_IDENTIFIER_MAX_LENGTH = 1024;
    private static final Set<String> PLATFORMS = Set.of(
        "GOOGLE_MEET",
        "ZOOM",
        "TEAMS",
        "WHATSAPP",
        "OTHER"
    );
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMeetingChannelRepository projectMeetingChannelRepository;

    MeetingChannelServiceImpl(
        UserRepository userRepository,
        ProjectRepository projectRepository,
        ProjectMemberRepository projectMemberRepository,
        ProjectMeetingChannelRepository projectMeetingChannelRepository
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectMeetingChannelRepository = projectMeetingChannelRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingChannelDto> listForSupervisor(String authenticatedUserId, String projectId) {
        User supervisor = resolveUser(authenticatedUserId, Roles.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        return projectMeetingChannelRepository.findByProjectIdPendingFirst(parsedProjectId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingChannelDto> listForStudent(String authenticatedUserId, String projectId) {
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

        return projectMeetingChannelRepository.findByProjectIdPendingFirst(parsedProjectId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional
    public MeetingChannelDto createAsSupervisor(
        String authenticatedUserId,
        String projectId,
        CreateMeetingChannelRequest request
    ) {
        User supervisor = resolveUser(authenticatedUserId, Roles.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        Project project = projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        Instant now = Instant.now();
        ProjectMeetingChannel channel = new ProjectMeetingChannel();
        channel.setProjectId(project.getId());
        channel.setPlatform(validatePlatform(request.getPlatform()));
        channel.setChannelName(validateChannelName(request.getChannelName()));
        channel.setLinkOrIdentifier(validateLinkOrIdentifier(request.getLinkOrIdentifier()));
        channel.setAddedBy(supervisor.getId());
        channel.setAddedByName(resolveUserDisplayName(supervisor));
        channel.setAddedByRole(Roles.SUPERVISOR);
        channel.setStatus(STATUS_APPROVED);
        channel.setApprovedBy(supervisor.getId());
        channel.setApprovedByName(resolveUserDisplayName(supervisor));
        channel.setApprovedAt(now);

        ProjectMeetingChannel saved = projectMeetingChannelRepository.save(channel);
        return toDto(saved);
    }

    @Override
    @Transactional
    public MeetingChannelDto createAsStudent(
        String authenticatedUserId,
        String projectId,
        CreateMeetingChannelRequest request
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

        Project project = projectRepository.findByIdAndDeletedAtIsNull(parsedProjectId)
            .orElseThrow(EntityNotFoundException::new);

        ProjectMeetingChannel channel = new ProjectMeetingChannel();
        channel.setProjectId(project.getId());
        channel.setPlatform(validatePlatform(request.getPlatform()));
        channel.setChannelName(validateChannelName(request.getChannelName()));
        channel.setLinkOrIdentifier(validateLinkOrIdentifier(request.getLinkOrIdentifier()));
        channel.setAddedBy(student.getId());
        channel.setAddedByName(resolveUserDisplayName(student));
        channel.setAddedByRole(Roles.STUDENT);
        channel.setStatus(STATUS_PENDING);
        channel.setApprovedBy(null);
        channel.setApprovedByName(null);
        channel.setApprovedAt(null);

        ProjectMeetingChannel saved = projectMeetingChannelRepository.save(channel);
        return toDto(saved);
    }

    @Override
    @Transactional
    public MeetingChannelDto updateAsSupervisor(
        String authenticatedUserId,
        String projectId,
        String channelId,
        UpdateMeetingChannelRequest request
    ) {
        User supervisor = resolveUser(authenticatedUserId, Roles.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        UUID parsedChannelId = parseChannelId(channelId);
        ProjectMeetingChannel channel = projectMeetingChannelRepository
            .findByIdAndProjectId(parsedChannelId, parsedProjectId)
            .orElseThrow(EntityNotFoundException::new);

        channel.setPlatform(validatePlatform(request.getPlatform()));
        channel.setChannelName(validateChannelName(request.getChannelName()));
        channel.setLinkOrIdentifier(validateLinkOrIdentifier(request.getLinkOrIdentifier()));
        ProjectMeetingChannel saved = projectMeetingChannelRepository.save(channel);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void deleteAsSupervisor(String authenticatedUserId, String projectId, String channelId) {
        User supervisor = resolveUser(authenticatedUserId, Roles.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        UUID parsedChannelId = parseChannelId(channelId);
        ProjectMeetingChannel channel = projectMeetingChannelRepository
            .findByIdAndProjectId(parsedChannelId, parsedProjectId)
            .orElseThrow(EntityNotFoundException::new);

        projectMeetingChannelRepository.delete(channel);
    }

    @Override
    @Transactional
    public MeetingChannelDto approveAsSupervisor(String authenticatedUserId, String projectId, String channelId) {
        User supervisor = resolveUser(authenticatedUserId, Roles.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        UUID parsedChannelId = parseChannelId(channelId);
        ProjectMeetingChannel channel = projectMeetingChannelRepository
            .findByIdAndProjectId(parsedChannelId, parsedProjectId)
            .orElseThrow(EntityNotFoundException::new);

        if (!STATUS_PENDING.equals(channel.getStatus())) {
            throw new ValidationException("status", "Only pending meeting channels can be approved.");
        }

        Instant now = Instant.now();
        channel.setStatus(STATUS_APPROVED);
        channel.setApprovedBy(supervisor.getId());
        channel.setApprovedByName(resolveUserDisplayName(supervisor));
        channel.setApprovedAt(now);

        ProjectMeetingChannel saved = projectMeetingChannelRepository.save(channel);
        return toDto(saved);
    }

    private String validatePlatform(String value) {
        if (value == null) {
            throw new ValidationException("platform", "Platform is required.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!PLATFORMS.contains(normalized)) {
            throw new ValidationException("platform", "Unsupported meeting platform.");
        }
        return normalized;
    }

    private String validateChannelName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("channelName", "Channel name is required.");
        }
        if (trimmed.length() > CHANNEL_NAME_MAX_LENGTH) {
            throw new ValidationException(
                "channelName",
                "Channel name must be at most " + CHANNEL_NAME_MAX_LENGTH + " characters."
            );
        }
        return trimmed;
    }

    private String validateLinkOrIdentifier(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("linkOrIdentifier", "Link or identifier is required.");
        }
        if (trimmed.length() > LINK_OR_IDENTIFIER_MAX_LENGTH) {
            throw new ValidationException(
                "linkOrIdentifier",
                "Link or identifier must be at most " + LINK_OR_IDENTIFIER_MAX_LENGTH + " characters."
            );
        }
        return trimmed;
    }

    private MeetingChannelDto toDto(ProjectMeetingChannel channel) {
        return new MeetingChannelDto(
            channel.getId(),
            channel.getProjectId(),
            channel.getPlatform(),
            channel.getChannelName(),
            channel.getLinkOrIdentifier(),
            channel.getAddedBy(),
            channel.getAddedByName(),
            channel.getAddedByRole(),
            channel.getStatus(),
            channel.getApprovedBy(),
            channel.getApprovedByName(),
            channel.getApprovedAt(),
            channel.getCreatedAt(),
            channel.getUpdatedAt()
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
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
            + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return fullName.isEmpty() ? user.getEmail() : fullName;
    }

    private UUID parseProjectId(String projectId) {
        try {
            return UUID.fromString(projectId);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException();
        }
    }

    private UUID parseChannelId(String channelId) {
        try {
            return UUID.fromString(channelId);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException();
        }
    }
}

