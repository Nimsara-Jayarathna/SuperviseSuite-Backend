package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.memberships.entity.ProjectMember;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedSummaryDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedAcknowledgeDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestContinueDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestValidationDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallStartDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryPageDto;
import com.supervisesuite.backend.projects.dto.LinkProjectGitHubRepositoryRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryListingDto;
import com.supervisesuite.backend.projects.dto.GitHubAvailableRepositoriesDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.dto.UpdateRepositoryRequest;
import com.supervisesuite.backend.projects.integration.github.GitHubInstallationDisconnectedException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.supervisesuite.backend.projects.service.ProjectService;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import com.supervisesuite.backend.projects.service.githubv2.SetupCallbackService;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.service.githubv2.AccessSourceService;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoriesDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubAccessMetadata;
import com.supervisesuite.backend.projects.service.githubv2.AccessRequestService;

@Service
class SupervisorServiceImpl implements SupervisorService {

    private static final String DEFAULT_LIFECYCLE_STATUS = "PLANNING";
    private static final String DEFAULT_MILESTONE_STATUS = "PLANNED";
    private static final String CANCELLED_MILESTONE_STATUS = "CANCELLED";
    private static final String COMPLETED_MILESTONE_STATUS = "COMPLETED";
    private static final Set<String> ALLOWED_LIFECYCLE_STATUSES = Set.of(
            "PLANNING",
            "ACTIVE",
            "AT_RISK",
            "BEHIND",
            "COMPLETED");
    private static final Set<String> ALLOWED_MILESTONE_STATUSES = Set.of(
            "PLANNED",
            "IN_PROGRESS",
            "COMPLETED",
            "MISSED",
            "CANCELLED");

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final ProjectService projectService;
    private final GitHubAppIntegrationService gitHubAppIntegrationService;
    private final SetupCallbackService setupCallbackService;
    private final RepositoryLinkService repositoryLinkService;
    private final AccessSourceService accessSourceService;
    private final AccessRequestService accessRequestService;

    SupervisorServiceImpl(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectMilestoneRepository projectMilestoneRepository,
            ProjectService projectService,
            GitHubAppIntegrationService gitHubAppIntegrationService,
            SetupCallbackService setupCallbackService,
            RepositoryLinkService repositoryLinkService,
            AccessSourceService accessSourceService,
            AccessRequestService accessRequestService) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.projectService = projectService;
        this.gitHubAppIntegrationService = gitHubAppIntegrationService;
        this.setupCallbackService = setupCallbackService;
        this.repositoryLinkService = repositoryLinkService;
        this.accessSourceService = accessSourceService;
        this.accessRequestService = accessRequestService;
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
                    && !milestoneDate.isAfter(milestoneWindowEnd)) {
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
                recentProjects);
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
            UpdateSupervisorProjectRequest request) {
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
        if (request.getLeaderStudentId() != null) {
            validateLeaderAssignment(project.getId(), request.getLeaderStudentId());
            project.setLeaderUserId(request.getLeaderStudentId());
        }
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
            UpdateSupervisorProjectStatusRequest request) {
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
    public SupervisorProjectDetailDto updateRepository(
            String authenticatedUserId,
            String projectId,
            UpdateRepositoryRequest request) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);

        Instant now = Instant.now();
        String normalizedRepositoryUrl = trimToNull(request.getRepositoryUrl());
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);

        Project savedProject = projectRepository.save(project);
        if (normalizedRepositoryUrl == null) {
            repositoryLinkService.disconnectAllLinks(savedProject.getId());
        } else {
            repositoryLinkService.linkManualRepository(savedProject.getId(), normalizedRepositoryUrl, supervisor.getId());
        }
        return toProjectDetail(savedProject);
    }

    @Override
    @Transactional
    public SupervisorProjectDetailDto addProjectMembers(
            String authenticatedUserId,
            String projectId,
            AddSupervisorProjectMembersRequest request) {
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
            AddSupervisorProjectMilestoneRequest request) {
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

        refreshProjectProgressPercent(project);
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
            UpdateSupervisorProjectMilestoneRequest request) {
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

        refreshProjectProgressPercent(project);
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
            CreateSupervisorProjectRequest request) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        List<User> students = resolveStudents(request.getStudentIds());
        List<CreateSupervisorProjectRequest.InitialMilestone> requestedMilestones = request.getMilestones();

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
        project.setHealthNote(null);
        project.setLeaderUserId(resolveLeaderForCreate(request.getLeaderStudentId(), students));
        project.setMilestoneDate(earliestMilestoneDate);
        project.setLastActivityAt(now);
        project.setSupervisor(supervisor);

        Project savedProject = projectRepository.save(project);

        projectMemberRepository
                .save(buildProjectMember(savedProject.getId(), supervisor.getId(), Roles.SUPERVISOR, now));
        for (User student : students) {
            projectMemberRepository.save(buildProjectMember(savedProject.getId(), student.getId(), Roles.STUDENT, now));
        }

        List<CreateSupervisorProjectResponse.Milestone> milestones = new ArrayList<>();
        int sequenceNo = 1;
        for (CreateSupervisorProjectRequest.InitialMilestone requestMilestone : requestedMilestones) {
            ProjectMilestone milestone = new ProjectMilestone();
            milestone.setProjectId(savedProject.getId());
            milestone.setTitle(requestMilestone.getTitle().trim());
            milestone.setDescription(trimToNull(requestMilestone.getDescription()));
            milestone.setDueDate(requestMilestone.getDueDate());
            milestone.setStatus(DEFAULT_MILESTONE_STATUS);
            milestone.setSequenceNo(sequenceNo++);
            milestone.setCreatedBy(supervisor.getId());
            milestone.setCreatedAt(now);

            ProjectMilestone savedMilestone = projectMilestoneRepository.save(milestone);
            milestones.add(toCreateMilestone(savedMilestone));
        }

        refreshProjectProgressPercent(savedProject);
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
                students.stream().map(this::toStudentAssignment).toList(),
                toCreateLeaderAssignment(updatedProject.getLeaderUserId()),
                milestones);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubDashboardDto getProjectGitHubDashboard(
            String authenticatedUserId,
            String projectId,
            String linkedRepositoryId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
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
            int size) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
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
            int size) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);
        UUID parsedLinkedRepositoryId = parseLinkedRepositoryId(linkedRepositoryId);
        return projectService.getGitHubContributorsPage(project.getId(), null, parsedLinkedRepositoryId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public GitHubInstallationRepositoryPageDto getGitHubInstallationRepositories(
            String authenticatedUserId,
            String projectId,
            Long installationId,
            int page,
            Integer size) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        Project project = projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);
        return projectService.getInstallationRepositories(project.getId(), installationId, supervisor.getId(), page,
                size);
    }
    
    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubRepositoryListingDto getProjectRepositoriesInventory(
            String authenticatedUserId,
            String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        
        Project project = projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);
        
        List<com.supervisesuite.backend.projects.dto.GitHubAccessSourceDto> sources = accessSourceService.getProjectAccessSources(project.getId());
        
        List<GitHubAvailableRepositoriesDto> inventory = sources.stream()
                .map(source -> repositoryLinkService.getAvailableRepositories(source.getId(), authenticatedUserId))
                .toList();
        
        return new ProjectGitHubRepositoryListingDto(project.getId().toString(), inventory);
    }

    @Override
    @Transactional
    public GitHubAccessRequestCreateDto createGitHubRepositoryAccessRequest(
            String authenticatedUserId,
            String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);
        return gitHubAppIntegrationService.createProjectAccessRequest(parsedProjectId, supervisor.getId());
    }

    @Override
    @Transactional
    public GitHubAccessRequestValidationDto validateGitHubRepositoryAccessRequest(
            String authenticatedUserId,
            String projectId,
            String requestToken) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);
        return gitHubAppIntegrationService.validateProjectAccessRequest(
                parsedProjectId,
                supervisor.getId(),
                requestToken);
    }

    @Override
    @Transactional
    public GitHubAccessRequestContinueDto continueGitHubRepositoryAccessRequest(
            String authenticatedUserId,
            String projectId,
            String requestToken) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);
        return gitHubAppIntegrationService.continueProjectAccessRequest(
                parsedProjectId,
                supervisor.getId(),
                requestToken);
    }

    @Override
    @Transactional(readOnly = true)
    public String buildGitHubSetupStartUrl(
            String authenticatedUserId,
            String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);

        GitHubInstallStartDto setup = setupCallbackService.startDirectInstall(
                parsedProjectId.toString(),
                supervisor.getId().toString());
        return setup.getGithubAuthorizeUrl();
    }

    @Override
    @Transactional
    public ProjectGitHubRepositoryLinkDto linkProjectGitHubRepository(
            String authenticatedUserId,
            String projectId,
            LinkProjectGitHubRepositoryRequest request) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);

        ProjectGitHubRepositoryLinkDto linkedRepository = projectService.linkProjectToInstallationRepository(
                project.getId(),
                request.getInstallationId(),
                request.getRepositoryId(),
                supervisor.getId());

        Instant now = Instant.now();
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        projectRepository.save(project);

        return linkedRepository;
    }

    @Override
    @Transactional
    public SupervisorProjectDetailDto removeProjectGitHubAccessAuthorization(
            String authenticatedUserId,
            String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);

        Instant now = Instant.now();
        repositoryLinkService.disconnectAllLinks(project.getId());
        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        Project savedProject = projectRepository.save(project);

        return toProjectDetail(savedProject);
    }

    @Override
    @Transactional(noRollbackFor = GitHubInstallationDisconnectedException.class)
    public void refreshProjectGitHubData(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);

        ProjectGitHubAccessMetadata accessMetadata = repositoryLinkService.resolveLink(project.getId());
        String effectiveUrl = accessMetadata != null ? accessMetadata.primaryRepositoryUrl() : null;

        projectService.refreshGitHubData(project.getId(), effectiveUrl);
    }

    private SupervisorProjectDetailDto toProjectDetail(Project project) {
        ProjectGitHubRepositoriesDto githubRepositories = null;
        try {
            githubRepositories = repositoryLinkService.getProjectRepositories(
                project.getId().toString(),
                project.getSupervisor().getId().toString()
            );
        } catch (Exception ignored) {
        }

        ProjectGitHubAccessMetadata accessMetadata = repositoryLinkService.resolveLink(project.getId());
        String effectiveUrl = accessMetadata != null ? accessMetadata.primaryRepositoryUrl() : null;

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
                projectService.getGitHubPreview(project.getId(), effectiveUrl),
                githubRepositories,
                project.getLastActivityAt(),
                toDetailLeader(project.getLeaderUserId()),
                getProjectMembers(project.getId()),
                getProjectMilestones(project.getId()));
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


    @Override
    @Transactional(readOnly = true)
    public GitHubAccessUpdatedSummaryDto getGitHubAccessUpdatedSummary(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);
        
        return accessRequestService.getPendingSummary(parsedProjectId);
    }

    @Override
    @Transactional
    public GitHubAccessUpdatedAcknowledgeDto acknowledgeGitHubAccessUpdated(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(parsedProjectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);
        
        accessRequestService.acknowledgePending(parsedProjectId);
        return new GitHubAccessUpdatedAcknowledgeDto(parsedProjectId);
    }

    private ProjectMember buildProjectMember(
            UUID projectId,
            UUID userId,
            String memberRole,
            Instant createdAt) {
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
                user.getRegistrationNumber());
    }

    private CreateSupervisorProjectResponse.StudentAssignment toStudentAssignment(User user) {
        return new CreateSupervisorProjectResponse.StudentAssignment(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRegistrationNumber());
    }

    private CreateSupervisorProjectResponse.StudentAssignment toCreateLeaderAssignment(UUID leaderUserId) {
        if (leaderUserId == null) {
            return null;
        }
        return userRepository.findById(leaderUserId)
                .map(this::toStudentAssignment)
                .orElse(null);
    }

    private CreateSupervisorProjectResponse.Milestone toCreateMilestone(ProjectMilestone milestone) {
        return new CreateSupervisorProjectResponse.Milestone(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getDueDate(),
                milestone.getStatus(),
                milestone.getSequenceNo());
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
                projectMemberRepository.countByProjectId(project.getId()));
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
                project.getHealthNote());
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
                member.getMemberRole());
    }

    private SupervisorProjectDetailDto.Leader toDetailLeader(UUID leaderUserId) {
        if (leaderUserId == null) {
            return null;
        }
        return userRepository.findById(leaderUserId)
                .map(this::toDetailLeader)
                .orElse(null);
    }

    private SupervisorProjectDetailDto.Leader toDetailLeader(User user) {
        return new SupervisorProjectDetailDto.Leader(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRegistrationNumber());
    }

    private SupervisorProjectDetailDto.Milestone toDetailMilestone(ProjectMilestone milestone) {
        return new SupervisorProjectDetailDto.Milestone(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getDueDate(),
                milestone.getStatus(),
                milestone.getSequenceNo());
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

    private UUID resolveLeaderForCreate(UUID leaderStudentId, List<User> students) {
        if (leaderStudentId == null) {
            return null;
        }

        boolean leaderIncluded = students.stream()
                .map(User::getId)
                .anyMatch(id -> Objects.equals(id, leaderStudentId));
        if (!leaderIncluded) {
            throw new ValidationException(
                    "leaderStudentId",
                    "Leader must be one of the selected student members.");
        }

        return leaderStudentId;
    }

    private void validateLeaderAssignment(UUID projectId, UUID leaderStudentId) {
        boolean isStudentMember = projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(
                leaderStudentId,
                projectId,
                Roles.STUDENT);
        if (!isStudentMember) {
            throw new ValidationException(
                    "leaderStudentId",
                    "Leader must be an assigned student of this project.");
        }
    }

    private void refreshProjectProgressPercent(Project project) {
        List<ProjectMilestone> milestones = projectMilestoneRepository
                .findByProjectIdOrderBySequenceNoAsc(project.getId());
        project.setProgressPercent(calculateProgressPercent(milestones));
    }

    private int calculateProgressPercent(List<ProjectMilestone> milestones) {
        long activeMilestones = milestones.stream()
                .filter(milestone -> !CANCELLED_MILESTONE_STATUS.equals(milestone.getStatus()))
                .count();

        if (activeMilestones == 0) {
            return 0;
        }

        long completedMilestones = milestones.stream()
                .filter(milestone -> !CANCELLED_MILESTONE_STATUS.equals(milestone.getStatus()))
                .filter(milestone -> COMPLETED_MILESTONE_STATUS.equals(milestone.getStatus()))
                .count();

        return (int) Math.round((completedMilestones * 100.0) / activeMilestones);
    }

    private String validateLifecycleStatus(String rawStatus) {
        String lifecycleStatus = rawStatus.trim().toUpperCase();
        if (!ALLOWED_LIFECYCLE_STATUSES.contains(lifecycleStatus)) {
            throw new ValidationException("lifecycleStatus", "Lifecycle status is invalid.");
        }
        return lifecycleStatus;
    }
}
