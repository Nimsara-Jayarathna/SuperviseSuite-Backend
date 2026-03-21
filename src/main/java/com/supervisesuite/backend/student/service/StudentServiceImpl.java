package com.supervisesuite.backend.student.service;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.memberships.entity.ProjectMember;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.student.dto.StudentProjectDetailDto;
import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import com.supervisesuite.backend.projects.service.ProjectService;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
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
    StudentServiceImpl(
         UserRepository userRepository,
         ProjectMemberRepository projectMemberRepository,
         ProjectRepository projectRepository,
         ProjectMilestoneRepository projectMilestoneRepository,
         ProjectService projectService
) {
         this.userRepository = userRepository;
         this.projectMemberRepository = projectMemberRepository;
         this.projectRepository = projectRepository;
         this.projectMilestoneRepository = projectMilestoneRepository;
         this.projectService = projectService;
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

        return new StudentProjectDetailDto(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getBatch(),
            project.getSemester(),
            project.getMilestoneDate(),
            project.getLastActivityAt(),
            project.getProgressPercent(),
            project.getHealthNote(),
            project.getRepositoryUrl(),
            projectService.getGitHubPreview(project.getId(), project.getRepositoryUrl()),
            members,
            milestones
        );
    }

    @Override
@Transactional(readOnly = true)
public ProjectGitHubDashboardDto getProjectGitHubDashboard(String authenticatedUserId, String projectId) {
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

    return projectService.getGitHubDashboard(project.getRepositoryUrl());
}

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getProjectGitHubActivityPage(
        String authenticatedUserId,
        String projectId,
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

        return projectService.getGitHubActivityPage(project.getId(), project.getRepositoryUrl(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getProjectGitHubContributorsPage(
        String authenticatedUserId,
        String projectId,
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

        return projectService.getGitHubContributorsPage(project.getId(), project.getRepositoryUrl(), page, size);
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

    private UUID parseProjectId(String projectId) {
        try {
            return UUID.fromString(projectId);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException();
        }
    }
}
