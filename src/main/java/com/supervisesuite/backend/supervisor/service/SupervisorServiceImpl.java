package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.memberships.entity.ProjectMember;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupervisorServiceImpl implements SupervisorService {

    private static final String DEFAULT_LIFECYCLE_STATUS = "PLANNING";
    private static final String DEFAULT_MILESTONE_STATUS = "PLANNED";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;

    SupervisorServiceImpl(
        UserRepository userRepository,
        ProjectRepository projectRepository,
        ProjectMemberRepository projectMemberRepository,
        ProjectMilestoneRepository projectMilestoneRepository
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSearchResultDto> searchStudents(String query) {
        String normalizedQuery = NormalizationUtils.normalizeEmail(query);
        if (normalizedQuery == null || normalizedQuery.length() < 3) {
            return List.of();
        }

        return userRepository
            .findTop10ByRoleAndEmailContainingIgnoreCaseOrderByEmailAsc(Roles.STUDENT, normalizedQuery)
            .stream()
            .map(this::toStudentSearchResult)
            .toList();
    }

    @Override
    @Transactional
    public CreateSupervisorProjectResponse createProject(
        String authenticatedUserId,
        CreateSupervisorProjectRequest request
    ) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        List<User> students = resolveStudents(request.getStudentIds());

        Instant now = Instant.now();

        Project project = new Project();
        project.setCreatedAt(now);
        project.setName(request.getTitle().trim());
        project.setDescription(request.getSummary().trim());
        project.setBatch(request.getBatch().trim());
        project.setSemester(request.getSemester().trim());
        project.setStatus(DEFAULT_LIFECYCLE_STATUS);
        project.setProgressPercent(0);
        project.setHealthNote(null);
        project.setMilestoneDate(request.getMilestone().getDueDate());
        project.setLastActivityAt(now);
        project.setSupervisor(supervisor);

        Project savedProject = projectRepository.save(project);

        projectMemberRepository.save(buildProjectMember(savedProject.getId(), supervisor.getId(), Roles.SUPERVISOR, now));
        for (User student : students) {
            projectMemberRepository.save(buildProjectMember(savedProject.getId(), student.getId(), Roles.STUDENT, now));
        }

        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setProjectId(savedProject.getId());
        milestone.setTitle(request.getMilestone().getTitle().trim());
        milestone.setDescription(trimToNull(request.getMilestone().getDescription()));
        milestone.setDueDate(request.getMilestone().getDueDate());
        milestone.setStatus(DEFAULT_MILESTONE_STATUS);
        milestone.setSequenceNo(1);
        milestone.setCreatedBy(supervisor.getId());
        milestone.setCreatedAt(now);

        ProjectMilestone savedMilestone = projectMilestoneRepository.save(milestone);

        return new CreateSupervisorProjectResponse(
            savedProject.getId(),
            savedProject.getName(),
            savedProject.getDescription(),
            savedProject.getBatch(),
            savedProject.getSemester(),
            savedProject.getStatus(),
            savedProject.getProgressPercent(),
            savedProject.getMilestoneDate(),
            students.stream().map(this::toStudentAssignment).toList(),
            new CreateSupervisorProjectResponse.Milestone(
                savedMilestone.getId(),
                savedMilestone.getTitle(),
                savedMilestone.getDescription(),
                savedMilestone.getDueDate(),
                savedMilestone.getStatus(),
                savedMilestone.getSequenceNo()
            )
        );
    }

    private User resolveSupervisor(String authenticatedUserId) {
        UUID supervisorId;
        try {
            supervisorId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Authentication required.");
        }

        User supervisor = userRepository.findById(supervisorId)
            .orElseThrow(() -> new UnauthorizedException("Authentication required."));

        if (!Roles.SUPERVISOR.equals(supervisor.getRole())) {
            throw new UnauthorizedException("Authentication required.");
        }

        return supervisor;
    }

    private List<User> resolveStudents(List<UUID> requestedStudentIds) {
        Set<UUID> uniqueIds = new LinkedHashSet<>(requestedStudentIds);
        if (uniqueIds.size() != requestedStudentIds.size()) {
            throw new ValidationException("studentIds", "Duplicate students are not allowed.");
        }

        List<User> students = userRepository.findAllById(uniqueIds);
        if (students.size() != uniqueIds.size()) {
            throw new ValidationException("studentIds", "One or more selected students were not found.");
        }

        boolean containsNonStudent = students.stream()
            .anyMatch(user -> !Roles.STUDENT.equals(user.getRole()));
        if (containsNonStudent) {
            throw new ValidationException("studentIds", "Only student accounts can be assigned to a project.");
        }

        return students;
    }

    private ProjectMember buildProjectMember(
        UUID projectId,
        UUID userId,
        String memberRole,
        Instant createdAt
    ) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setMemberRole(memberRole);
        member.setCreatedAt(createdAt);
        return member;
    }

    private StudentSearchResultDto toStudentSearchResult(User user) {
        return new StudentSearchResultDto(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getRegistrationNumber()
        );
    }

    private CreateSupervisorProjectResponse.StudentAssignment toStudentAssignment(User user) {
        return new CreateSupervisorProjectResponse.StudentAssignment(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getRegistrationNumber()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
