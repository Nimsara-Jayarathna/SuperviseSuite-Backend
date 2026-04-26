package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.EntityIdParser;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.dto.UpdateRepositoryRequest;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.service.milestones.MilestonePolicyEngine;
import com.supervisesuite.backend.projects.service.milestones.ProjectMilestoneAggregateService;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectStatusRequest;
import com.supervisesuite.backend.users.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupervisorProjectCommandService {

    private static final String DEFAULT_LIFECYCLE_STATUS = "PLANNING";
    private static final String DEFAULT_MILESTONE_STATUS = "PLANNED";
    private static final Set<String> ALLOWED_LIFECYCLE_STATUSES = Set.of(
            "PLANNING",
            "ACTIVE",
            "AT_RISK",
            "BEHIND",
            "COMPLETED");

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final RepositoryLinkService repositoryLinkService;
    private final MilestonePolicyEngine milestonePolicyEngine;
    private final ProjectMilestoneAggregateService projectMilestoneAggregateService;
    private final ProjectAccessGuard projectAccessGuard;
    private final SupervisorProjectDtoMapper projectDtoMapper;
    private final SupervisorProjectMemberService projectMemberService;

    SupervisorProjectCommandService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectMilestoneRepository projectMilestoneRepository,
            RepositoryLinkService repositoryLinkService,
            MilestonePolicyEngine milestonePolicyEngine,
            ProjectMilestoneAggregateService projectMilestoneAggregateService,
            ProjectAccessGuard projectAccessGuard,
            SupervisorProjectDtoMapper projectDtoMapper,
            SupervisorProjectMemberService projectMemberService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.repositoryLinkService = repositoryLinkService;
        this.milestonePolicyEngine = milestonePolicyEngine;
        this.projectMilestoneAggregateService = projectMilestoneAggregateService;
        this.projectAccessGuard = projectAccessGuard;
        this.projectDtoMapper = projectDtoMapper;
        this.projectMemberService = projectMemberService;
    }

    @Transactional
    SupervisorProjectDetailDto updateProject(
            User supervisor,
            String projectId,
            UpdateSupervisorProjectRequest request) {
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parseProjectId(projectId));

        String lifecycleStatus = validateLifecycleStatus(request.getLifecycleStatus());

        Instant now = Instant.now();
        project.setName(request.getTitle().trim());
        project.setDescription(request.getSummary().trim());
        project.setBatch(request.getBatch().trim());
        project.setSemester(request.getSemester().trim());
        project.setStatus(lifecycleStatus);
        if (request.getLeaderStudentId() != null) {
            projectMemberService.validateLeaderAssignment(project.getId(), request.getLeaderStudentId());
            project.setLeaderUserId(request.getLeaderStudentId());
        }
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);

        Project savedProject = projectRepository.save(project);
        return projectDtoMapper.toProjectDetail(savedProject);
    }

    @Transactional
    SupervisorProjectDetailDto updateProjectStatus(
            User supervisor,
            String projectId,
            UpdateSupervisorProjectStatusRequest request) {
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parseProjectId(projectId));

        String lifecycleStatus = validateLifecycleStatus(request.getLifecycleStatus());
        Instant now = Instant.now();

        project.setStatus(lifecycleStatus);
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);

        Project savedProject = projectRepository.save(project);
        return projectDtoMapper.toProjectDetail(savedProject);
    }

    @Transactional
    SupervisorProjectDetailDto updateRepository(
            User supervisor,
            String projectId,
            UpdateRepositoryRequest request) {
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parseProjectId(projectId));

        Instant now = Instant.now();
        String normalizedRepositoryUrl = NormalizationUtils.trimToNull(request.getRepositoryUrl());
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);

        Project savedProject = projectRepository.save(project);
        if (normalizedRepositoryUrl == null) {
            repositoryLinkService.disconnectAllLinks(savedProject.getId());
        } else {
            repositoryLinkService.linkManualRepository(savedProject.getId(), normalizedRepositoryUrl, supervisor.getId());
        }
        return projectDtoMapper.toProjectDetail(savedProject);
    }

    @Transactional
    CreateSupervisorProjectResponse createProject(
            User supervisor,
            CreateSupervisorProjectRequest request) {
        List<User> students = projectMemberService.resolveStudents(request.getStudentIds());
        List<CreateSupervisorProjectRequest.InitialMilestone> requestedMilestones = request.getMilestones();
        LocalDate today = LocalDate.now();

        LocalDate previousDueDate = null;
        for (CreateSupervisorProjectRequest.InitialMilestone requestedMilestone : requestedMilestones) {
            LocalDate currentDueDate = requestedMilestone.getDueDate();
            milestonePolicyEngine.validateDueDateForStatus(currentDueDate, DEFAULT_MILESTONE_STATUS, today);
            milestonePolicyEngine.validateChronologyWithPrevious(previousDueDate, currentDueDate);
            previousDueDate = currentDueDate;
        }

        Instant now = Instant.now();
        LocalDate earliestMilestoneDate = requestedMilestones.stream()
                .map(CreateSupervisorProjectRequest.InitialMilestone::getDueDate)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        Project project = new Project();
        project.setCreatedAt(now);
        project.setName(request.getTitle().trim());
        project.setDescription(request.getSummary().trim());
        project.setBatch(request.getBatch().trim());
        project.setSemester(request.getSemester().trim());
        project.setStatus(DEFAULT_LIFECYCLE_STATUS);
        project.setProgressPercent(0);
        project.setLeaderUserId(projectMemberService.resolveLeaderForCreate(request.getLeaderStudentId(), students));
        project.setMilestoneDate(earliestMilestoneDate);
        project.setLastActivityAt(now);
        project.setSupervisor(supervisor);

        Project savedProject = projectRepository.save(project);

        projectMemberRepository
                .save(projectMemberService.buildProjectMember(savedProject.getId(), supervisor.getId(), Roles.SUPERVISOR, now));
        for (User student : students) {
            projectMemberRepository.save(projectMemberService.buildProjectMember(savedProject.getId(), student.getId(), Roles.STUDENT, now));
        }

        List<CreateSupervisorProjectResponse.Milestone> milestones = new ArrayList<>();
        List<ProjectMilestone> createdMilestones = new ArrayList<>(requestedMilestones.size());
        int sequenceNo = 1;
        for (CreateSupervisorProjectRequest.InitialMilestone requestMilestone : requestedMilestones) {
            ProjectMilestone milestone = new ProjectMilestone();
            milestone.setProjectId(savedProject.getId());
            milestone.setTitle(requestMilestone.getTitle().trim());
            milestone.setDescription(NormalizationUtils.trimToNull(requestMilestone.getDescription()));
            milestone.setDueDate(requestMilestone.getDueDate());
            milestone.setStatus(DEFAULT_MILESTONE_STATUS);
            milestone.setSequenceNo(sequenceNo++);
            milestone.setCreatedBy(supervisor.getId());
            milestone.setCreatedAt(now);

            ProjectMilestone savedMilestone = projectMilestoneRepository.save(milestone);
            createdMilestones.add(savedMilestone);
            milestones.add(projectDtoMapper.toCreateMilestone(savedMilestone));
        }

        projectMilestoneAggregateService.applyTo(savedProject, createdMilestones);
        Project updatedProject = projectRepository.save(savedProject);

        return new CreateSupervisorProjectResponse(
                updatedProject.getId(),
                updatedProject.getName(),
                updatedProject.getDescription(),
                updatedProject.getBatch(),
                updatedProject.getSemester(),
                updatedProject.getStatus(),
                updatedProject.getProgressPercent(),
                updatedProject.getMilestoneDate(),
                students.stream().map(projectDtoMapper::toStudentAssignment).toList(),
                projectDtoMapper.toCreateLeaderAssignment(updatedProject.getLeaderUserId()),
                milestones);
    }

    private UUID parseProjectId(String projectId) {
        return EntityIdParser.parseOrNotFound(projectId);
    }

    private String validateLifecycleStatus(String rawStatus) {
        String lifecycleStatus = rawStatus.trim().toUpperCase();
        if (!ALLOWED_LIFECYCLE_STATUSES.contains(lifecycleStatus)) {
            throw new ValidationException("lifecycleStatus", "Lifecycle status is invalid.");
        }
        return lifecycleStatus;
    }
}
