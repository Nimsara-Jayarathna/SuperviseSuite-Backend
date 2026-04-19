package com.supervisesuite.backend.student.service;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.memberships.entity.ProjectMember;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.dto.JiraHierarchyDto;
import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoriesDto;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.student.dto.StudentProjectDetailDto;
import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import com.supervisesuite.backend.projects.service.ProjectService;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.dto.ProjectGitHubAccessMetadata;
import com.supervisesuite.backend.projects.service.jira.JiraHealthService;
import com.supervisesuite.backend.projects.service.jira.JiraSprintProgressService;
import com.supervisesuite.backend.projects.service.jira.JiraWorkloadService;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileListDto;
import com.supervisesuite.backend.projectfiles.service.ProjectFileAccessRole;
import com.supervisesuite.backend.projectfiles.service.ProjectFileService;
import com.supervisesuite.backend.meetings.dto.CreateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.CreateMeetingRecordRequest;
import com.supervisesuite.backend.meetings.dto.MeetingChannelDto;
import com.supervisesuite.backend.meetings.dto.MeetingRecordDto;
import com.supervisesuite.backend.meetings.service.MeetingChannelService;
import com.supervisesuite.backend.meetings.service.MeetingRecordService;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StudentServiceImpl implements StudentService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final ProjectService projectService;
    private final RepositoryLinkService repositoryLinkService;
    private final ProjectJiraIntegrationRepository projectJiraIntegrationRepository;
    private final ProjectJiraIssueRepository projectJiraIssueRepository;
    private final JiraHealthService jiraHealthService;
    private final JiraSprintProgressService jiraSprintProgressService;
    private final JiraWorkloadService jiraWorkloadService;
    private final ProjectFileService projectFileService;
    private final MeetingChannelService meetingChannelService;
    private final MeetingRecordService meetingRecordService;

    StudentServiceImpl(
         UserRepository userRepository,
         ProjectMemberRepository projectMemberRepository,
         ProjectRepository projectRepository,
         ProjectMilestoneRepository projectMilestoneRepository,
         ProjectService projectService,
         RepositoryLinkService repositoryLinkService,
         ProjectJiraIntegrationRepository projectJiraIntegrationRepository,
         ProjectJiraIssueRepository projectJiraIssueRepository,
            JiraHealthService jiraHealthService,
            JiraSprintProgressService jiraSprintProgressService,
            JiraWorkloadService jiraWorkloadService,
            ProjectFileService projectFileService,
            MeetingChannelService meetingChannelService,
            MeetingRecordService meetingRecordService
    ) {
         this.userRepository = userRepository;
         this.projectMemberRepository = projectMemberRepository;
         this.projectRepository = projectRepository;
         this.projectMilestoneRepository = projectMilestoneRepository;
         this.projectService = projectService;
         this.repositoryLinkService = repositoryLinkService;
         this.projectJiraIntegrationRepository = projectJiraIntegrationRepository;
         this.projectJiraIssueRepository = projectJiraIssueRepository;
         this.jiraHealthService = jiraHealthService;
         this.jiraSprintProgressService = jiraSprintProgressService;
            this.jiraWorkloadService = jiraWorkloadService;
            this.projectFileService = projectFileService;
            this.meetingChannelService = meetingChannelService;
            this.meetingRecordService = meetingRecordService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentProjectSummaryDto> getProjects(String authenticatedUserId) {
        User student = resolveStudent(authenticatedUserId);

        List<ProjectMember> memberships = projectMemberRepository
            .findByUserIdAndMemberRoleOrderByCreatedAtDesc(student.getId(), Roles.STUDENT);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Set<UUID> uniqueProjectIds = memberships.stream()
            .map(ProjectMember::getProjectId)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);

        return projectRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(uniqueProjectIds)
            .stream()
            .map(this::toProjectSummary)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentProjectDetailDto getProjectById(String authenticatedUserId, String projectId) {
        User student = resolveStudent(authenticatedUserId);
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

        List<ProjectMember> projectMembers = projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(project.getId());
        List<UUID> memberIds = projectMembers.stream()
            .map(ProjectMember::getUserId)
            .toList();
        Map<UUID, User> userById = new HashMap<>();
        userRepository.findAllById(memberIds).forEach(user -> userById.put(user.getId(), user));

        List<StudentProjectDetailDto.Member> members = projectMembers.stream()
            .map(member -> toDetailMember(member, userById.get(member.getUserId())))
            .filter(member -> member != null)
            .toList();

        List<StudentProjectDetailDto.Milestone> milestones = projectMilestoneRepository
            .findByProjectIdOrderBySequenceNoAsc(project.getId())
            .stream()
            .map(this::toDetailMilestone)
            .toList();

        StudentProjectDetailDto.Leader leader = null;
        if (project.getLeaderUserId() != null) {
            User leaderUser = userById.get(project.getLeaderUserId());
            if (leaderUser == null) {
                leaderUser = userRepository.findById(project.getLeaderUserId()).orElse(null);
            }
            if (leaderUser != null) {
                leader = toDetailLeader(leaderUser);
            }
        }

        ProjectGitHubAccessMetadata accessMetadata = repositoryLinkService.resolveLink(project.getId());
        String effectiveUrl = accessMetadata != null ? accessMetadata.primaryRepositoryUrl() : null;
        ProjectGitHubRepositoriesDto githubRepositories = repositoryLinkService.getProjectRepositories(
            project.getId().toString(),
            project.getSupervisor().getId().toString()
        );
        ProjectJiraIntegration jiraIntegration = projectJiraIntegrationRepository
            .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(project.getId())
            .orElse(null);

        StudentProjectDetailDto detail = new StudentProjectDetailDto(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getBatch(),
            project.getSemester(),
            project.getMilestoneDate(),
            project.getLastActivityAt(),
            project.getProgressPercent(),
            projectService.getGitHubPreview(project.getId(), effectiveUrl),
            githubRepositories,
            new StudentProjectDetailDto.JiraIntegration(
                jiraIntegration != null,
                jiraIntegration != null ? jiraIntegration.getWorkspaceName() : null,
                jiraIntegration != null ? jiraIntegration.getWorkspaceUrl() : null,
                jiraIntegration != null
                    ? (jiraIntegration.getLastSyncedAt() != null
                        ? jiraIntegration.getLastSyncedAt()
                        : jiraIntegration.getConnectedAt())
                    : null,
                jiraIntegration != null ? jiraIntegration.getSyncStatus() : null
            ),
            leader,
            members,
            milestones
        );
        ProjectFileListDto files = projectFileService.listFiles(
            student.getId().toString(),
            project.getId().toString(),
            ProjectFileAccessRole.STUDENT
        );
        detail.setFiles(new StudentProjectDetailDto.Files(files.files(), files.config()));
        return detail;
    }

    @Override
@Transactional(readOnly = true)
public ProjectGitHubDashboardDto getProjectGitHubDashboard(
    String authenticatedUserId,
    String projectId,
    String linkedRepositoryId
) {
    User student = resolveStudent(authenticatedUserId);
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

    UUID parsedLinkedRepositoryId = parseLinkedRepositoryId(linkedRepositoryId);
    return projectService.getGitHubDashboard(project.getId(), null, parsedLinkedRepositoryId);
}

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getProjectGitHubActivityPage(
        String authenticatedUserId,
        String projectId,
        String linkedRepositoryId,
        int page,
        int size
    ) {
        User student = resolveStudent(authenticatedUserId);
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

        UUID parsedLinkedRepositoryId = parseLinkedRepositoryId(linkedRepositoryId);
        return projectService.getGitHubActivityPage(project.getId(), null, parsedLinkedRepositoryId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getProjectGitHubContributorsPage(
        String authenticatedUserId,
        String projectId,
        String linkedRepositoryId,
        int page,
        int size
    ) {
        User student = resolveStudent(authenticatedUserId);
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

        UUID parsedLinkedRepositoryId = parseLinkedRepositoryId(linkedRepositoryId);
        return projectService.getGitHubContributorsPage(project.getId(), null, parsedLinkedRepositoryId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public JiraHealthDto getJiraHealthOverview(String authenticatedUserId, String projectId) {
        User student = resolveStudent(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        boolean hasAccess = projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(
            student.getId(),
            parsedProjectId,
            Roles.STUDENT
        );
        if (!hasAccess) {
            throw new EntityNotFoundException();
        }

        return jiraHealthService.getHealthOverview(parsedProjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public JiraSprintProgressDto getJiraSprintProgress(String authenticatedUserId, String projectId) {
        User student = resolveStudent(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        boolean hasAccess = projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(
            student.getId(),
            parsedProjectId,
            Roles.STUDENT
        );
        if (!hasAccess) {
            throw new EntityNotFoundException();
        }

        return jiraSprintProgressService.getSprintProgress(parsedProjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public JiraWorkloadDto getJiraWorkload(String authenticatedUserId, String projectId) {
        User student = resolveStudent(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        boolean hasAccess = projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(
            student.getId(),
            parsedProjectId,
            Roles.STUDENT
        );
        if (!hasAccess) {
            throw new EntityNotFoundException();
        }

        return jiraWorkloadService.getWorkload(parsedProjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public JiraHierarchyDto getJiraHierarchy(String authenticatedUserId, String projectId) {
        User student = resolveStudent(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        boolean hasAccess = projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(
            student.getId(),
            parsedProjectId,
            Roles.STUDENT
        );
        if (!hasAccess) {
            throw new EntityNotFoundException();
        }

        List<ProjectJiraIssue> issues = projectJiraIssueRepository.findAllByProjectId(parsedProjectId);

        Map<String, JiraHierarchyDto.JiraHierarchyNodeDto> nodeMap = new LinkedHashMap<>();
        for (ProjectJiraIssue issue : issues) {
            JiraHierarchyDto.JiraHierarchyNodeDto node = new JiraHierarchyDto.JiraHierarchyNodeDto();
            node.setIssueKey(issue.getIssueKey());
            node.setSummary(issue.getSummary());
            node.setIssueType(issue.getIssueType());
            node.setStatus(issue.getStatusName());
            node.setPriority(issue.getPriorityName());
            node.setAssigneeDisplayName(issue.getAssigneeDisplayName());
            node.setStoryPoints(issue.getStoryPoints() == null ? null : issue.getStoryPoints().intValue());
            node.setChildren(new ArrayList<>());
            nodeMap.put(issue.getIssueKey(), node);
        }

        List<JiraHierarchyDto.JiraHierarchyNodeDto> roots = new ArrayList<>();
        List<JiraHierarchyDto.JiraHierarchyNodeDto> orphans = new ArrayList<>();

        for (ProjectJiraIssue issue : issues) {
            JiraHierarchyDto.JiraHierarchyNodeDto node = nodeMap.get(issue.getIssueKey());
            String parentKey = issue.getParentKey();

            if (parentKey == null || parentKey.isBlank()) {
                roots.add(node);
            } else if (nodeMap.containsKey(parentKey)) {
                nodeMap.get(parentKey).getChildren().add(node);
            } else {
                orphans.add(node);
            }
        }

        JiraHierarchyDto dto = new JiraHierarchyDto();
        dto.setRoots(roots);
        dto.setOrphans(orphans);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingChannelDto> getProjectMeetingChannels(String authenticatedUserId, String projectId) {
        return meetingChannelService.listForStudent(authenticatedUserId, projectId);
    }

    @Override
    @Transactional
    public MeetingChannelDto addProjectMeetingChannel(
        String authenticatedUserId,
        String projectId,
        CreateMeetingChannelRequest request
    ) {
        return meetingChannelService.createAsStudent(authenticatedUserId, projectId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingRecordDto> getProjectMeetingRecords(String authenticatedUserId, String projectId) {
        return meetingRecordService.listForStudent(authenticatedUserId, projectId);
    }

    @Override
    @Transactional
    public MeetingRecordDto addProjectMeetingRecord(
        String authenticatedUserId,
        String projectId,
        CreateMeetingRecordRequest request
    ) {
        return meetingRecordService.createAsStudent(authenticatedUserId, projectId, request);
    }

    private User resolveStudent(String authenticatedUserId) {
        UUID studentId;
        try {
            studentId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Authentication required.");
        }

        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new UnauthorizedException("Authentication required."));

        if (!Roles.STUDENT.equals(student.getRole())) {
            throw new UnauthorizedException("Authentication required.");
        }

        return student;
    }

    private StudentProjectSummaryDto toProjectSummary(Project project) {
        User supervisor = project.getSupervisor();
        String supervisorName = null;
        if (supervisor != null) {
            String fullName = ((supervisor.getFirstName() == null ? "" : supervisor.getFirstName()) + " "
                + (supervisor.getLastName() == null ? "" : supervisor.getLastName())).trim();
            supervisorName = fullName.isEmpty() ? supervisor.getEmail() : fullName;
        }

        return new StudentProjectSummaryDto(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getBatch(),
            project.getSemester(),
            project.getMilestoneDate(),
            project.getLastActivityAt(),
            project.getProgressPercent(),
            supervisorName
        );
    }

    private StudentProjectDetailDto.Member toDetailMember(ProjectMember member, User user) {
        if (user == null) {
            return null;
        }

        return new StudentProjectDetailDto.Member(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getRegistrationNumber(),
            member.getMemberRole()
        );
    }

    private StudentProjectDetailDto.Milestone toDetailMilestone(ProjectMilestone milestone) {
        return new StudentProjectDetailDto.Milestone(
            milestone.getId(),
            milestone.getTitle(),
            milestone.getDescription(),
            milestone.getDueDate(),
            milestone.getStatus(),
            milestone.getSequenceNo()
        );
    }

    private StudentProjectDetailDto.Leader toDetailLeader(User user) {
        return new StudentProjectDetailDto.Leader(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getRegistrationNumber()
        );
    }

    private UUID parseProjectId(String projectId) {
        try {
            return UUID.fromString(projectId);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException();
        }
    }

    private UUID parseLinkedRepositoryId(String linkedRepositoryId) {
        if (linkedRepositoryId == null || linkedRepositoryId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(linkedRepositoryId.trim());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("linkedRepositoryId", "linkedRepositoryId must be a valid UUID.");
        }
    }
}
