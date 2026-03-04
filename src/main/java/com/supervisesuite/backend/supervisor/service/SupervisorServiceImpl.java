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
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMembersRequest;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.SupervisorDashboardDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectSummaryDto;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectStatusRequest;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupervisorServiceImpl implements SupervisorService {

    private static final String DEFAULT_LIFECYCLE_STATUS = "PLANNING";
    private static final String DEFAULT_MILESTONE_STATUS = "PLANNED";
    private static final Set<String> ALLOWED_LIFECYCLE_STATUSES = Set.of(
        "PLANNING",
        "ACTIVE",
        "AT_RISK",
        "BEHIND",
        "COMPLETED"
    );
    private static final Set<String> ALLOWED_MILESTONE_STATUSES = Set.of(
        "PLANNED",
        "IN_PROGRESS",
        "COMPLETED",
        "MISSED",
        "CANCELLED"
    );

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
    public SupervisorDashboardDto getDashboard(String authenticatedUserId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        List<Project> projects = projectRepository
            .findBySupervisorIdAndDeletedAtIsNullOrderByCreatedAtDesc(supervisor.getId());

        int planningProjects = 0;
        int activeProjects = 0;
        int atRiskProjects = 0;
        int behindProjects = 0;
        int completedProjects = 0;
        int upcomingMilestonesCount = 0;

        LocalDate today = LocalDate.now();
        LocalDate milestoneWindowEnd = today.plusDays(14);

        for (Project project : projects) {
            String lifecycleStatus = project.getStatus();
            if ("PLANNING".equals(lifecycleStatus)) {
                planningProjects++;
            } else if ("ACTIVE".equals(lifecycleStatus)) {
                activeProjects++;
            } else if ("AT_RISK".equals(lifecycleStatus)) {
                atRiskProjects++;
            } else if ("BEHIND".equals(lifecycleStatus)) {
                behindProjects++;
            } else if ("COMPLETED".equals(lifecycleStatus)) {
                completedProjects++;
            }

            LocalDate milestoneDate = project.getMilestoneDate();
            if (milestoneDate != null
                && !milestoneDate.isBefore(today)
                && !milestoneDate.isAfter(milestoneWindowEnd)
            ) {
                upcomingMilestonesCount++;
            }
        }

        List<SupervisorDashboardDto.ProjectItem> dashboardProjects = projects.stream()
            .map(this::toDashboardProjectItem)
            .toList();

        List<SupervisorDashboardDto.ProjectItem> recentProjects = projects.stream()
            .sorted(Comparator
                .comparing(Project::getLastActivityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Project::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(5)
            .map(this::toDashboardProjectItem)
            .toList();

        return new SupervisorDashboardDto(
            projects.size(),
            planningProjects,
            activeProjects,
            atRiskProjects,
            behindProjects,
            completedProjects,
            upcomingMilestonesCount,
            dashboardProjects,
            recentProjects
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupervisorProjectSummaryDto> getProjects(String authenticatedUserId) {
        User supervisor = resolveSupervisor(authenticatedUserId);

        return projectRepository.findBySupervisorIdAndDeletedAtIsNullOrderByCreatedAtDesc(supervisor.getId())
            .stream()
            .map(this::toProjectSummary)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SupervisorProjectDetailDto getProjectById(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        return toProjectDetail(project);
    }

    @Override
    @Transactional
    public SupervisorProjectDetailDto updateProject(
        String authenticatedUserId,
        String projectId,
        UpdateSupervisorProjectRequest request
    ) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        String lifecycleStatus = validateLifecycleStatus(request.getLifecycleStatus());

        Instant now = Instant.now();
        project.setName(request.getTitle().trim());
        project.setDescription(request.getSummary().trim());
        project.setBatch(request.getBatch().trim());
        project.setSemester(request.getSemester().trim());
        project.setStatus(lifecycleStatus);
        project.setHealthNote(trimToNull(request.getHealthNote()));
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);

        Project savedProject = projectRepository.save(project);
        return toProjectDetail(savedProject);
    }

    @Override
    @Transactional
    public SupervisorProjectDetailDto updateProjectStatus(
        String authenticatedUserId,
        String projectId,
        UpdateSupervisorProjectStatusRequest request
    ) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        String lifecycleStatus = validateLifecycleStatus(request.getLifecycleStatus());
        Instant now = Instant.now();

        project.setStatus(lifecycleStatus);
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);

        Project savedProject = projectRepository.save(project);
        return toProjectDetail(savedProject);
    }

    @Override
    @Transactional
    public SupervisorProjectDetailDto addProjectMembers(
        String authenticatedUserId,
        String projectId,
        AddSupervisorProjectMembersRequest request
    ) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        List<User> studentsToAdd = resolveStudents(request.getStudentIds());
        for (User student : studentsToAdd) {
            if (projectMemberRepository.existsByUserIdAndProjectId(student.getId(), project.getId())) {
                throw new ValidationException("studentIds", "One or more selected students are already assigned.");
            }
        }

        Instant now = Instant.now();
        for (User student : studentsToAdd) {
            projectMemberRepository.save(buildProjectMember(project.getId(), student.getId(), Roles.STUDENT, now));
        }

        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        projectRepository.save(project);

        return toProjectDetail(project);
    }

    @Override
    @Transactional
    public SupervisorProjectDetailDto addProjectMilestone(
        String authenticatedUserId,
        String projectId,
        AddSupervisorProjectMilestoneRequest request
    ) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        Integer nextSequenceNo = projectMilestoneRepository.findTopByProjectIdOrderBySequenceNoDesc(project.getId())
            .map(milestone -> milestone.getSequenceNo() + 1)
            .orElse(1);

        Instant now = Instant.now();
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setProjectId(project.getId());
        milestone.setTitle(request.getTitle().trim());
        milestone.setDescription(trimToNull(request.getDescription()));
        milestone.setDueDate(request.getDueDate());
        milestone.setStatus(DEFAULT_MILESTONE_STATUS);
        milestone.setSequenceNo(nextSequenceNo);
        milestone.setCreatedBy(supervisor.getId());
        milestone.setCreatedAt(now);
        projectMilestoneRepository.save(milestone);

        project.setUpdatedAt(now);
        project.setMilestoneDate(request.getDueDate());
        project.setLastActivityAt(now);
        projectRepository.save(project);

        return toProjectDetail(project);
    }

    @Override
    @Transactional
    public SupervisorProjectDetailDto updateProjectMilestone(
        String authenticatedUserId,
        String projectId,
        String milestoneId,
        UpdateSupervisorProjectMilestoneRequest request
    ) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        UUID parsedMilestoneId = parseMilestoneId(milestoneId);

        Project project = projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
            .orElseThrow(EntityNotFoundException::new);

        ProjectMilestone milestone = projectMilestoneRepository.findByIdAndProjectId(parsedMilestoneId, project.getId())
            .orElseThrow(EntityNotFoundException::new);

        String milestoneStatus = request.getStatus().trim().toUpperCase();
        if (!ALLOWED_MILESTONE_STATUSES.contains(milestoneStatus)) {
            throw new ValidationException("status", "Milestone status is invalid.");
        }

        Instant now = Instant.now();
        milestone.setTitle(request.getTitle().trim());
        milestone.setDescription(trimToNull(request.getDescription()));
        milestone.setDueDate(request.getDueDate());
        milestone.setStatus(milestoneStatus);
        milestone.setUpdatedAt(now);
        projectMilestoneRepository.save(milestone);

        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        projectRepository.save(project);

        return toProjectDetail(project);
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

    private SupervisorProjectDetailDto toProjectDetail(Project project) {
        return new SupervisorProjectDetailDto(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getBatch(),
            project.getSemester(),
            project.getMilestoneDate(),
            project.getProgressPercent(),
            project.getHealthNote(),
            project.getLastActivityAt(),
            getProjectMembers(project.getId()),
            getProjectMilestones(project.getId())
        );
    }

    private List<SupervisorProjectDetailDto.Member> getProjectMembers(UUID projectId) {
        List<ProjectMember> projectMembers = projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        List<UUID> memberIds = projectMembers.stream()
            .map(ProjectMember::getUserId)
            .toList();
        Map<UUID, User> userById = new HashMap<>();
        userRepository.findAllById(memberIds).forEach(user -> userById.put(user.getId(), user));

        return projectMembers.stream()
            .map(member -> toDetailMember(member, userById.get(member.getUserId())))
            .filter(member -> member != null)
            .toList();
    }

    private List<SupervisorProjectDetailDto.Milestone> getProjectMilestones(UUID projectId) {
        return projectMilestoneRepository
            .findByProjectIdOrderBySequenceNoAsc(projectId)
            .stream()
            .map(this::toDetailMilestone)
            .toList();
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

    private SupervisorProjectSummaryDto toProjectSummary(Project project) {
        return new SupervisorProjectSummaryDto(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getBatch(),
            project.getSemester(),
            project.getMilestoneDate(),
            project.getProgressPercent(),
            project.getHealthNote(),
            projectMemberRepository.countByProjectId(project.getId())
        );
    }

    private SupervisorDashboardDto.ProjectItem toDashboardProjectItem(Project project) {
        return new SupervisorDashboardDto.ProjectItem(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getMilestoneDate(),
            project.getLastActivityAt(),
            project.getProgressPercent(),
            project.getHealthNote()
        );
    }

    private SupervisorProjectDetailDto.Member toDetailMember(ProjectMember member, User user) {
        if (user == null) {
            return null;
        }

        return new SupervisorProjectDetailDto.Member(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getRegistrationNumber(),
            member.getMemberRole()
        );
    }

    private SupervisorProjectDetailDto.Milestone toDetailMilestone(ProjectMilestone milestone) {
        return new SupervisorProjectDetailDto.Milestone(
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

    private UUID parseMilestoneId(String milestoneId) {
        try {
            return UUID.fromString(milestoneId);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException();
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String validateLifecycleStatus(String rawStatus) {
        String lifecycleStatus = rawStatus.trim().toUpperCase();
        if (!ALLOWED_LIFECYCLE_STATUSES.contains(lifecycleStatus)) {
            throw new ValidationException("lifecycleStatus", "Lifecycle status is invalid.");
        }
        return lifecycleStatus;
    }
}
