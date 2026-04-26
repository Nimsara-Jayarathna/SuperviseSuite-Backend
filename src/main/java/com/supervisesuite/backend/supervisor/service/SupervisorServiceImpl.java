package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.ApiErrorDetail;
import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.common.util.EntityIdParser;
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
import com.supervisesuite.backend.projects.dto.JiraAuthUrlDto;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteRequestDto;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteResultDto;
import com.supervisesuite.backend.projects.dto.UpdateRepositoryRequest;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.entity.ProjectJiraOAuthState;
import com.supervisesuite.backend.projects.integration.github.GitHubInstallationDisconnectedException;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraOAuthStateRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.jira.JiraTokenEncryptionService;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.dto.JiraHierarchyDto;
import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import com.supervisesuite.backend.projects.service.ProjectService;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import com.supervisesuite.backend.projects.service.githubv2.SetupCallbackService;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.service.githubv2.AccessSourceService;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoriesDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubAccessMetadata;
import com.supervisesuite.backend.projects.service.githubv2.AccessRequestService;
import com.supervisesuite.backend.projects.service.jira.JiraHealthService;
import com.supervisesuite.backend.projects.service.jira.JiraIssueSyncService;
import com.supervisesuite.backend.projects.service.jira.JiraSprintProgressService;
import com.supervisesuite.backend.projects.service.jira.JiraWorkloadService;
import com.supervisesuite.backend.projects.service.milestones.MilestonePolicyEngine;
import com.supervisesuite.backend.projects.service.milestones.ProjectMilestoneAggregateService;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileListDto;
import com.supervisesuite.backend.projectfiles.service.ProjectFileAccessRole;
import com.supervisesuite.backend.projectfiles.service.ProjectFileService;
import com.supervisesuite.backend.meetings.dto.CreateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.CreateMeetingRecordRequest;
import com.supervisesuite.backend.meetings.dto.MeetingChannelDto;
import com.supervisesuite.backend.meetings.dto.MeetingRecordDto;
import com.supervisesuite.backend.meetings.dto.UpdateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.UpdateMeetingRecordRequest;
import com.supervisesuite.backend.meetings.service.MeetingChannelService;
import com.supervisesuite.backend.meetings.service.MeetingRecordService;

@Service
class SupervisorServiceImpl implements SupervisorService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SupervisorServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String DEFAULT_LIFECYCLE_STATUS = "PLANNING";
    private static final String DEFAULT_MILESTONE_STATUS = "PLANNED";
    private static final String DEFAULT_JIRA_SCOPE = "read:jira-user read:jira-work offline_access";
    private static final String REQUIRED_JIRA_SCOPE = "offline_access";
    private static final Set<String> ALLOWED_LIFECYCLE_STATUSES = Set.of(
            "PLANNING",
            "ACTIVE",
            "AT_RISK",
            "BEHIND",
            "COMPLETED");

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
    private final JiraProperties jiraProperties;
    private final ProjectJiraIntegrationRepository projectJiraIntegrationRepository;
    private final ProjectJiraOAuthStateRepository projectJiraOAuthStateRepository;
    private final JiraTokenEncryptionService jiraTokenEncryptionService;
    private final ProjectJiraIssueRepository projectJiraIssueRepository;
    private final JiraIssueSyncService jiraIssueSyncService;
    private final JiraHealthService jiraHealthService;
    private final JiraSprintProgressService jiraSprintProgressService;
    private final JiraWorkloadService jiraWorkloadService;
    private final ProjectFileService projectFileService;
    private final MeetingChannelService meetingChannelService;
    private final MeetingRecordService meetingRecordService;
    private final RestClient restClient;
    private final MilestonePolicyEngine milestonePolicyEngine;
    private final ProjectMilestoneAggregateService projectMilestoneAggregateService;
    private final ProjectAccessGuard projectAccessGuard;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingJiraWorkspaceSelection> pendingJiraWorkspaceSelections = new ConcurrentHashMap<>();
    private static final long JIRA_WORKSPACE_SELECTION_TTL_SECONDS = 600L;

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
            AccessRequestService accessRequestService,
            JiraProperties jiraProperties,
            ProjectJiraIntegrationRepository projectJiraIntegrationRepository,
            ProjectJiraOAuthStateRepository projectJiraOAuthStateRepository,
            JiraTokenEncryptionService jiraTokenEncryptionService,
            ProjectJiraIssueRepository projectJiraIssueRepository,
            JiraIssueSyncService jiraIssueSyncService,
            JiraHealthService jiraHealthService,
            JiraSprintProgressService jiraSprintProgressService,
            JiraWorkloadService jiraWorkloadService,
            ProjectFileService projectFileService,
            MeetingChannelService meetingChannelService,
            MeetingRecordService meetingRecordService,
            RestClient.Builder restClientBuilder,
            MilestonePolicyEngine milestonePolicyEngine,
            ProjectMilestoneAggregateService projectMilestoneAggregateService,
            ProjectAccessGuard projectAccessGuard) {
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
        this.jiraProperties = jiraProperties;
        this.projectJiraIntegrationRepository = projectJiraIntegrationRepository;
        this.projectJiraOAuthStateRepository = projectJiraOAuthStateRepository;
        this.jiraTokenEncryptionService = jiraTokenEncryptionService;
        this.projectJiraIssueRepository = projectJiraIssueRepository;
        this.jiraIssueSyncService = jiraIssueSyncService;
        this.jiraHealthService = jiraHealthService;
        this.jiraSprintProgressService = jiraSprintProgressService;
        this.jiraWorkloadService = jiraWorkloadService;
        this.projectFileService = projectFileService;
        this.meetingChannelService = meetingChannelService;
        this.meetingRecordService = meetingRecordService;
        this.restClient = restClientBuilder.build();
        this.milestonePolicyEngine = milestonePolicyEngine;
        this.projectMilestoneAggregateService = projectMilestoneAggregateService;
        this.projectAccessGuard = projectAccessGuard;
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

        // Jira health signals — additive, independent of the manual status counters above
        List<UUID> projectIds = projects.stream().map(Project::getId).toList();
        Set<UUID> connectedProjectIds = projectJiraIntegrationRepository
                .findAllByProjectIdInAndRevokedAtIsNull(projectIds)
                .stream()
                .map(com.supervisesuite.backend.projects.entity.ProjectJiraIntegration::getProjectId)
                .collect(java.util.stream.Collectors.toSet());

        int jiraAtRiskCount = 0;
        int jiraBehindCount = 0;
        Map<UUID, String> jiraIndicators = new HashMap<>();

        // TODO: optimize to single aggregate query per project batch (currently 3 queries per connected project)
        for (Project project : projects) {
            UUID pid = project.getId();
            if (!connectedProjectIds.contains(pid)) {
                jiraIndicators.put(pid, "NOT_CONNECTED");
                continue;
            }
            long total = projectJiraIssueRepository.countByProjectId(pid);
            if (total == 0) {
                jiraIndicators.put(pid, "HEALTHY");
                continue;
            }
            long done = projectJiraIssueRepository.countByProjectIdAndStatusCategoryKey(pid, "done");
            long overdue = projectJiraIssueRepository
                    .countByProjectIdAndDueDateBeforeAndStatusCategoryKeyNot(pid, today, "done");
            double completionPct = (double) done / total * 100.0;
            String indicator;
            if (overdue > 2) {
                indicator = "AT_RISK";
                jiraAtRiskCount++;
            } else if (completionPct < 50.0) {
                indicator = "BEHIND";
                jiraBehindCount++;
            } else {
                indicator = "HEALTHY";
            }
            jiraIndicators.put(pid, indicator);
        }

        List<SupervisorDashboardDto.ProjectItem> dashboardProjects = projects.stream()
                .map(p -> toDashboardProjectItem(
                        p,
                        p.getMilestoneDate(),
                        jiraIndicators.getOrDefault(p.getId(), "NOT_CONNECTED")))
                .toList();

        List<SupervisorDashboardDto.ProjectItem> recentProjects = projects.stream()
                .sorted(Comparator
                        .comparing(Project::getLastActivityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Project::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(p -> toDashboardProjectItem(
                        p,
                        p.getMilestoneDate(),
                        jiraIndicators.getOrDefault(p.getId(), "NOT_CONNECTED")))
                .toList();

        SupervisorDashboardDto dashboard = new SupervisorDashboardDto(
                projects.size(),
                planningProjects,
                activeProjects,
                atRiskProjects,
                behindProjects,
                completedProjects,
                upcomingMilestonesCount,
                dashboardProjects,
                recentProjects);
        dashboard.setJiraAtRiskCount(jiraAtRiskCount);
        dashboard.setJiraBehindCount(jiraBehindCount);
        return dashboard;
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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        SupervisorProjectDetailDto detail = toProjectDetail(project);
        ProjectFileListDto files = projectFileService.listFiles(
                supervisor.getId().toString(),
                project.getId().toString(),
                ProjectFileAccessRole.SUPERVISOR);
        detail.setFiles(new SupervisorProjectDetailDto.Files(files.files(), files.config()));
        return detail;
    }

    @Override
    @Transactional
    public SupervisorProjectDetailDto updateProject(
            String authenticatedUserId,
            String projectId,
            UpdateSupervisorProjectRequest request) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        String lifecycleStatus = validateLifecycleStatus(request.getLifecycleStatus());

        Instant now = Instant.now();
        project.setName(request.getTitle().trim());
        project.setDescription(request.getSummary().trim());
        project.setBatch(request.getBatch().trim());
        project.setSemester(request.getSemester().trim());
        project.setStatus(lifecycleStatus);
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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        List<ProjectMilestone> existingMilestones = projectMilestoneRepository
                .findByProjectIdOrderBySequenceNoAsc(project.getId());
        Integer nextSequenceNo = existingMilestones.isEmpty()
                ? 1
                : existingMilestones.get(existingMilestones.size() - 1).getSequenceNo() + 1;

        LocalDate dueDate = request.getDueDate();
        LocalDate today = LocalDate.now();
        milestonePolicyEngine.validateDueDateForStatus(dueDate, DEFAULT_MILESTONE_STATUS, today);
        LocalDate previousDueDate = existingMilestones.isEmpty()
                ? null
                : existingMilestones.get(existingMilestones.size() - 1).getDueDate();
        milestonePolicyEngine.validateChronologyWithPrevious(previousDueDate, dueDate);

        Instant now = Instant.now();
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setProjectId(project.getId());
        milestone.setTitle(request.getTitle().trim());
        milestone.setDescription(NormalizationUtils.trimToNull(request.getDescription()));
        milestone.setDueDate(dueDate);
        milestone.setStatus(DEFAULT_MILESTONE_STATUS);
        milestone.setSequenceNo(nextSequenceNo);
        milestone.setCreatedBy(supervisor.getId());
        milestone.setCreatedAt(now);
        projectMilestoneRepository.save(milestone);

        List<ProjectMilestone> milestonesForAggregates = new ArrayList<>(existingMilestones.size() + 1);
        milestonesForAggregates.addAll(existingMilestones);
        milestonesForAggregates.add(milestone);
        projectMilestoneAggregateService.applyTo(project, milestonesForAggregates);
        project.setUpdatedAt(now);
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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        ProjectMilestone milestone = projectMilestoneRepository.findByIdAndProjectId(parsedMilestoneId, project.getId())
                .orElseThrow(EntityNotFoundException::new);

        String milestoneStatus = milestonePolicyEngine.normalizeAndValidateStatus(request.getStatus());
        LocalDate requestedDueDate = request.getDueDate();
        String currentStatus = milestone.getStatus();
        LocalDate currentDueDate = milestone.getDueDate();
        boolean statusChanged = !Objects.equals(currentStatus, milestoneStatus);
        boolean dueDateChanged = !Objects.equals(currentDueDate, requestedDueDate);

        if (statusChanged) {
            milestonePolicyEngine.validateStatusTransition(currentStatus, milestoneStatus);
        }
        if (statusChanged || dueDateChanged) {
            milestonePolicyEngine.validateDueDateForStatus(requestedDueDate, milestoneStatus, LocalDate.now());
        }

        List<ProjectMilestone> milestonesForAggregates = null;
        if (dueDateChanged) {
            milestonesForAggregates = projectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(project.getId());
            milestonePolicyEngine.validateChronologyForUpdate(milestonesForAggregates, milestone.getId(), requestedDueDate);
        }

        Instant now = Instant.now();
        milestone.setTitle(request.getTitle().trim());
        milestone.setDescription(NormalizationUtils.trimToNull(request.getDescription()));
        milestone.setDueDate(requestedDueDate);
        milestone.setStatus(milestoneStatus);
        milestone.setUpdatedAt(now);
        projectMilestoneRepository.save(milestone);

        if (statusChanged || dueDateChanged) {
            if (milestonesForAggregates != null) {
                for (ProjectMilestone candidate : milestonesForAggregates) {
                    if (Objects.equals(candidate.getId(), milestone.getId())) {
                        candidate.setDueDate(requestedDueDate);
                        candidate.setStatus(milestoneStatus);
                        break;
                    }
                }
            }
            List<ProjectMilestone> orderedMilestones = milestonesForAggregates != null
                    ? milestonesForAggregates
                    : projectMilestoneAggregateService.loadOrderedMilestones(project.getId());
            projectMilestoneAggregateService.applyTo(project, orderedMilestones);
        }
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
            milestones.add(toCreateMilestone(savedMilestone));
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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
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
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
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
        
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
        
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
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
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
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
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
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
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
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

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

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

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

        ProjectJiraIntegration jiraIntegration = projectJiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(project.getId())
                .orElse(null);

        MilestoneDetailView milestoneDetailView = buildMilestoneDetailView(project.getId());

        SupervisorProjectDetailDto detailDto = new SupervisorProjectDetailDto(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getBatch(),
                project.getSemester(),
                milestoneDetailView.milestoneDate(),
                project.getProgressPercent(),
                projectService.getGitHubPreview(project.getId(), effectiveUrl),
                githubRepositories,
                new SupervisorProjectDetailDto.JiraIntegration(
                        jiraIntegration != null,
                        jiraIntegration != null ? jiraIntegration.getWorkspaceName() : null,
                    jiraIntegration != null ? jiraIntegration.getWorkspaceUrl() : null,
                    jiraIntegration != null
                        ? (jiraIntegration.getLastSyncedAt() != null
                            ? jiraIntegration.getLastSyncedAt()
                            : jiraIntegration.getConnectedAt())
                        : null,
                    jiraIntegration != null ? jiraIntegration.getTokenExpiresAt() : null,
                    jiraIntegration != null ? jiraIntegration.getSyncStatus() : null),
                project.getLastActivityAt(),
                toDetailLeader(project.getLeaderUserId()),
                getProjectMembers(project.getId()),
                milestoneDetailView.milestones());
        detailDto.setMilestoneInsights(milestoneDetailView.insights());
        return detailDto;
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

    private MilestoneDetailView buildMilestoneDetailView(UUID projectId) {
        List<ProjectMilestone> projectMilestones = projectMilestoneRepository.findByProjectIdOrderBySequenceNoAsc(projectId);
        MilestonePolicyEngine.MilestoneInsightsSnapshot snapshot =
                milestonePolicyEngine.computeInsights(projectMilestones, LocalDate.now());

        List<SupervisorProjectDetailDto.Milestone> milestoneDtos = projectMilestones.stream()
                .map(milestone -> toDetailMilestone(
                        milestone,
                        snapshot.signalsByMilestoneId().get(milestone.getId())))
                .toList();
        SupervisorProjectDetailDto.MilestoneInsights insights = new SupervisorProjectDetailDto.MilestoneInsights(
                snapshot.overdueOpenMilestones(),
                snapshot.dueSoonCount(),
                snapshot.timelineRiskLevel());
        return new MilestoneDetailView(
                milestoneDtos,
                insights,
                milestonePolicyEngine.computeProjectMilestoneDate(projectMilestones));
    }

    private User resolveSupervisor(String authenticatedUserId) {
        return projectAccessGuard.requireSupervisor(authenticatedUserId);
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
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
        
        return accessRequestService.getPendingSummary(parsedProjectId);
    }

    @Override
    @Transactional
    public GitHubAccessUpdatedAcknowledgeDto acknowledgeGitHubAccessUpdated(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);
        
        accessRequestService.acknowledgePending(parsedProjectId);
        return new GitHubAccessUpdatedAcknowledgeDto(parsedProjectId);
    }

    @Override
    @Transactional
    public JiraAuthUrlDto getProjectJiraAuthUrl(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        ProjectJiraIntegration activeIntegration = projectJiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(project.getId())
                .orElse(null);
        if (activeIntegration != null) {
            String issue = "Jira is already connected for this project (" + activeIntegration.getWorkspaceName() + ").";
            throw new ValidationException(
                    issue,
                    List.of(new ApiErrorDetail("jira", issue)));
        }

        String clientId = NormalizationUtils.trimToNull(jiraProperties.getClientId());
        String redirectUri = NormalizationUtils.trimToNull(jiraProperties.getRedirectUri());
        if (clientId == null || redirectUri == null) {
            throw new ValidationException("jiraConfig", "Jira OAuth is not fully configured.");
        }

        String authTargetUrl = NormalizationUtils.defaultIfBlank(
                NormalizationUtils.trimToNull(jiraProperties.getAuthTargetUrl()),
                "https://auth.atlassian.com/authorize");
        String nonce = generateOpaqueToken();
        String state = nonce + ":" + parsedProjectId;
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(Math.max(60, jiraProperties.getOauthStateTtlSeconds()));

        ProjectJiraOAuthState oauthState = new ProjectJiraOAuthState();
        oauthState.setStateNonceHash(sha256Base64(nonce));
        oauthState.setProjectId(parsedProjectId);
        oauthState.setUserId(supervisor.getId());
        oauthState.setExpiresAt(expiresAt);
        oauthState.setCreatedAt(now);
        oauthState.setUpdatedAt(now);
        projectJiraOAuthStateRepository.saveAndFlush(oauthState);

        String url = authTargetUrl
            + "?audience=" + urlencode(NormalizationUtils.defaultIfBlank(
                NormalizationUtils.trimToNull(jiraProperties.getAudience()),
                "api.atlassian.com"))
            + "&client_id=" + urlencode(clientId)
            + "&scope=" + urlencode(normalizeJiraScope(jiraProperties.getScope()))
            + "&redirect_uri=" + urlencode(redirectUri)
            + "&state=" + urlencode(state)
            + "&response_type=code&prompt=consent";
        return new JiraAuthUrlDto(url);
    }

    @Override
    @Transactional
    public JiraOAuthCompleteResultDto completeJiraOAuth(String authenticatedUserId, JiraOAuthCompleteRequestDto request) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        cleanupExpiredPendingWorkspaceSelections(Instant.now());

        String selectionToken = NormalizationUtils.trimToNull(request.getSelectionToken());
        if (selectionToken != null) {
            return completeJiraOAuthWithWorkspaceSelection(supervisor, request, selectionToken);
        }

        String oauthError = NormalizationUtils.trimToNull(request.getError());
        String code = NormalizationUtils.trimToNull(request.getCode());
        String state = NormalizationUtils.trimToNull(request.getState());
        if (oauthError != null) {
            String issue = "access_denied".equalsIgnoreCase(oauthError)
                ? "Jira authorization was cancelled. No connection was saved."
                : "Jira authorization was not completed. "
                    + NormalizationUtils.defaultIfBlank(
                        NormalizationUtils.trimToNull(request.getErrorDescription()),
                        "Please try again.");
            throw new ValidationException(
                    issue,
                    List.of(new ApiErrorDetail("jiraOAuth", issue)));
        }

        if (code == null || state == null) {
            throw new ValidationException("jiraOAuth", "Missing OAuth code/state.");
        }

        String nonce = NormalizationUtils.trimToNull(state.split(":", 2)[0]);
        String projectIdRaw = state.contains(":") ? state.split(":", 2)[1] : null;
        if (nonce == null || projectIdRaw == null) {
            throw new ValidationException("state", "OAuth state format is invalid. Start Jira connection again.");
        }
        UUID projectId;
        try {
            projectId = UUID.fromString(projectIdRaw);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("state", "Invalid OAuth state project reference.");
        }

        String nonceHash = sha256Base64(nonce);
        Optional<ProjectJiraOAuthState> persistedStateOpt = projectJiraOAuthStateRepository.findByStateNonceHash(nonceHash);
        if (persistedStateOpt.isEmpty()) {
            throw new ValidationException("state", "OAuth state was not recognized. Start Jira connection again.");
        }
        ProjectJiraOAuthState persistedState = persistedStateOpt.get();
        Instant now = Instant.now();
        if (persistedState.getUsedAt() != null) {
            throw new ValidationException("state", "OAuth state was already used. Start Jira connection again.");
        }
        if (persistedState.getExpiresAt() == null || now.isAfter(persistedState.getExpiresAt())) {
            throw new ValidationException("state", "OAuth state expired. Start Jira connection again.");
        }
        if (!projectId.equals(persistedState.getProjectId())) {
            throw new ValidationException("state", "OAuth state project does not match this callback.");
        }
        if (!supervisor.getId().equals(persistedState.getUserId())) {
            throw new ValidationException("state", "OAuth state user does not match the current session.");
        }
        persistedState.setUsedAt(now);
        persistedState.setUpdatedAt(now);
        projectJiraOAuthStateRepository.save(persistedState);

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, projectId);

        String clientId = NormalizationUtils.trimToNull(jiraProperties.getClientId());
        String clientSecret = NormalizationUtils.trimToNull(jiraProperties.getClientSecret());
        String redirectUri = NormalizationUtils.trimToNull(jiraProperties.getRedirectUri());
        if (clientId == null || clientSecret == null || redirectUri == null) {
            throw new ValidationException("jiraConfig", "Jira OAuth is not fully configured.");
        }

        String tokenTargetUrl = NormalizationUtils.defaultIfBlank(
                NormalizationUtils.trimToNull(jiraProperties.getTokenTargetUrl()),
                "https://auth.atlassian.com/oauth/token");

        Map<String, Object> tokenRequest = new LinkedHashMap<>();
        tokenRequest.put("grant_type", "authorization_code");
        tokenRequest.put("client_id", clientId);
        tokenRequest.put("client_secret", clientSecret);
        tokenRequest.put("code", code);
        tokenRequest.put("redirect_uri", redirectUri);

        Map<?, ?> tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(tokenTargetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(tokenRequest)
                    .retrieve()
                    .body(Map.class);
        } catch (ResourceAccessException exception) {
            throw new ValidationException(
                "jiraOAuth",
                "Unable to reach Atlassian token endpoint from this environment.");
        } catch (RestClientResponseException exception) {
            throw new ValidationException("jiraOAuth", buildAtlassianTokenExchangeIssue(exception));
        }

        String accessToken = tokenResponse == null
                ? null
                : NormalizationUtils.trimToNull(String.valueOf(tokenResponse.get("access_token")));
        if (accessToken == null || "null".equalsIgnoreCase(accessToken)) {
            throw new ValidationException("jiraOAuth", "Atlassian token exchange returned no access token.");
        }
        String scopes = tokenResponse == null
                ? null
                : NormalizationUtils.trimToNull(String.valueOf(tokenResponse.get("scope")));
        String refreshToken = tokenResponse == null
                ? null
                : NormalizationUtils.trimToNull(String.valueOf(tokenResponse.get("refresh_token")));
        Number expiresInSecs = tokenResponse == null || tokenResponse.get("expires_in") == null ? null : (Number) tokenResponse.get("expires_in");
        Instant tokenExpiresAt = null;
        if (expiresInSecs != null) {
             tokenExpiresAt = Instant.now().plusSeconds(expiresInSecs.longValue());
        }

        List<Map<String, Object>> resources;
        try {
            resources = restClient.get()
                    .uri("https://api.atlassian.com/oauth/token/accessible-resources")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(List.class);
        } catch (ResourceAccessException exception) {
            throw new ValidationException(
                "jiraOAuth",
                "Unable to reach Atlassian API from this environment.");
        } catch (RestClientResponseException exception) {
            throw new ValidationException("jiraOAuth", "Unable to fetch Jira workspace details.");
        }

        if (resources == null || resources.isEmpty()) {
            throw new ValidationException("jiraOAuth", "No Jira workspace was returned for this authorization.");
        }

        if (resources.size() > 1) {
            String pendingToken = generateOpaqueToken();
            List<JiraOAuthCompleteResultDto.WorkspaceOption> workspaceOptions = mapWorkspaceOptions(resources);
            pendingJiraWorkspaceSelections.put(
                    pendingToken,
                    new PendingJiraWorkspaceSelection(
                            project.getId(),
                            supervisor.getId(),
                            accessToken,
                            refreshToken,
                            tokenExpiresAt,
                            scopes,
                            workspaceOptions,
                            Instant.now().plusSeconds(JIRA_WORKSPACE_SELECTION_TTL_SECONDS)));

            return new JiraOAuthCompleteResultDto(
                    project.getId().toString(),
                    null,
                    true,
                    pendingToken,
                    workspaceOptions);
        }

        Map<String, Object> workspace = resources.get(0);
        return persistWorkspaceIntegration(supervisor, project, accessToken, refreshToken, tokenExpiresAt, scopes, workspace);
    }

    private JiraOAuthCompleteResultDto completeJiraOAuthWithWorkspaceSelection(
            User supervisor,
            JiraOAuthCompleteRequestDto request,
            String selectionToken) {
        PendingJiraWorkspaceSelection pending = pendingJiraWorkspaceSelections.get(selectionToken);
        if (pending == null) {
            throw new ValidationException(
                    "jiraOAuth",
                    "Jira workspace selection has expired. Start Jira connection again.");
        }

        if (!supervisor.getId().equals(pending.userId())) {
            throw new ValidationException("jiraOAuth", "Jira workspace selection does not match the current session.");
        }

        if (Instant.now().isAfter(pending.expiresAt())) {
            pendingJiraWorkspaceSelections.remove(selectionToken);
            throw new ValidationException(
                    "jiraOAuth",
                    "Jira workspace selection has expired. Start Jira connection again.");
        }

        String selectedCloudId = NormalizationUtils.trimToNull(request.getSelectedCloudId());
        if (selectedCloudId == null) {
            throw new ValidationException("jiraOAuth", "Select a Jira workspace to continue.");
        }

        JiraOAuthCompleteResultDto.WorkspaceOption selectedWorkspace = pending.workspaceOptions().stream()
                .filter(option -> selectedCloudId.equals(option.cloudId()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "jiraOAuth",
                        "Selected Jira workspace is invalid. Please choose from the provided list."));

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, pending.projectId());

        Map<String, Object> workspace = new LinkedHashMap<>();
        workspace.put("id", selectedWorkspace.cloudId());
        workspace.put("name", selectedWorkspace.workspaceName());
        workspace.put("url", selectedWorkspace.workspaceUrl());

        JiraOAuthCompleteResultDto result = persistWorkspaceIntegration(
                supervisor,
                project,
                pending.accessToken(),
                pending.refreshToken(),
                pending.tokenExpiresAt(),
                pending.scopes(),
                workspace);

        pendingJiraWorkspaceSelections.remove(selectionToken);
        return result;
    }

    private JiraOAuthCompleteResultDto persistWorkspaceIntegration(
            User supervisor,
            Project project,
            String accessToken,
            String refreshToken,
            Instant tokenExpiresAt,
            String scopes,
            Map<String, Object> workspace) {
        String cloudId = NormalizationUtils.trimToNull(workspace == null ? null : (String) workspace.get("id"));
        String workspaceName = NormalizationUtils.trimToNull(workspace == null ? null : (String) workspace.get("name"));
        String workspaceUrl = NormalizationUtils.trimToNull(workspace == null ? null : (String) workspace.get("url"));
        if (cloudId == null || workspaceName == null) {
            throw new ValidationException("jiraOAuth", "Jira workspace details are incomplete.");
        }

        Instant revokeTimestamp = Instant.now();
        projectJiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(project.getId())
                .ifPresent(existing -> {
                    existing.setRevokedAt(revokeTimestamp);
                    existing.setUpdatedAt(revokeTimestamp);
                    projectJiraIntegrationRepository.save(existing);
                });

        Instant now = Instant.now();
        ProjectJiraIntegration integration = new ProjectJiraIntegration();
        integration.setProjectId(project.getId());
        integration.setCloudId(cloudId);
        integration.setWorkspaceName(workspaceName);
        integration.setWorkspaceUrl(workspaceUrl);
        integration.setAccessTokenEncrypted(jiraTokenEncryptionService.encrypt(accessToken));
        integration.setRefreshTokenEncrypted(refreshToken == null || "null".equalsIgnoreCase(refreshToken) ? null : jiraTokenEncryptionService.encrypt(refreshToken));
        integration.setTokenExpiresAt(tokenExpiresAt);
        integration.setScope(scopes);
        integration.setConnectedBy(supervisor.getId());
        integration.setConnectedAt(now);
        integration.setUpdatedAt(now);
        projectJiraIntegrationRepository.save(integration);

        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        projectRepository.save(project);

        triggerInitialJiraSyncAfterCommit(project.getId());

        return new JiraOAuthCompleteResultDto(
                project.getId().toString(),
                workspaceName,
                false,
                null,
                List.of());
    }

    private List<JiraOAuthCompleteResultDto.WorkspaceOption> mapWorkspaceOptions(List<Map<String, Object>> resources) {
        List<JiraOAuthCompleteResultDto.WorkspaceOption> options = new ArrayList<>();
        for (Map<String, Object> resource : resources) {
            String cloudId = NormalizationUtils.trimToNull(resource == null ? null : (String) resource.get("id"));
            String workspaceName = NormalizationUtils.trimToNull(resource == null ? null : (String) resource.get("name"));
            String workspaceUrl = NormalizationUtils.trimToNull(resource == null ? null : (String) resource.get("url"));
            if (cloudId == null || workspaceName == null) {
                continue;
            }
            options.add(new JiraOAuthCompleteResultDto.WorkspaceOption(cloudId, workspaceName, workspaceUrl));
        }

        if (options.isEmpty()) {
            throw new ValidationException("jiraOAuth", "Jira workspace details are incomplete.");
        }

        return options;
    }

    private void cleanupExpiredPendingWorkspaceSelections(Instant now) {
        pendingJiraWorkspaceSelections.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    private record PendingJiraWorkspaceSelection(
            UUID projectId,
            UUID userId,
            String accessToken,
            String refreshToken,
            Instant tokenExpiresAt,
            String scopes,
            List<JiraOAuthCompleteResultDto.WorkspaceOption> workspaceOptions,
            Instant expiresAt) {
    }

    private void triggerInitialJiraSyncAfterCommit(UUID projectId) {
        Runnable syncTask = () -> {
            try {
                jiraIssueSyncService.syncProjectIssues(projectId);
            } catch (Exception ex) {
                log.warn("Initial Jira issue sync failed for project {} after OAuth completion. "
                                + "Connection was saved successfully; cache will populate on next sync. Error: {}",
                        projectId,
                        ex.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncTask.run();
                }
            });
            return;
        }

        syncTask.run();
    }

    @Override
    @Transactional
    public SupervisorProjectDetailDto disconnectProjectJira(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        ProjectJiraIntegration activeIntegration = projectJiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(project.getId())
                .orElse(null);
        if (activeIntegration == null) {
            throw new ValidationException(
                    "Jira is not connected for this project.",
                    List.of(new ApiErrorDetail("jira", "Jira is not connected for this project.")));
        }
        if ("IN_PROGRESS".equals(activeIntegration.getSyncStatus())) {
            throw new ConflictException("Cannot disconnect Jira while sync is in progress.");
        }

        Instant now = Instant.now();
        projectJiraIntegrationRepository.delete(activeIntegration);
        projectJiraIssueRepository.deleteAllByProjectId(project.getId());

        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        Project savedProject = projectRepository.save(project);
        return toProjectDetail(savedProject);
    }

    @Override
    @Transactional(readOnly = true)
    public JiraHealthDto getJiraHealthOverview(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        return jiraHealthService.getHealthOverview(parsedProjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public JiraSprintProgressDto getJiraSprintProgress(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        return jiraSprintProgressService.getSprintProgress(parsedProjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public JiraWorkloadDto getJiraWorkload(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        return jiraWorkloadService.getWorkload(parsedProjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public JiraHierarchyDto getJiraHierarchy(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

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
    public JiraHealthDto refreshProjectJiraData(String authenticatedUserId, String projectId) {
        User supervisor = resolveSupervisor(authenticatedUserId);
        UUID parsedProjectId = parseProjectId(projectId);

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parsedProjectId);

        ProjectJiraIntegration activeIntegration = projectJiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(project.getId())
                .orElse(null);
        if (activeIntegration == null) {
            throw new ValidationException(
                    "Jira is not connected for this project.",
                    List.of(new ApiErrorDetail("jira", "Jira is not connected for this project.")));
        }

        Instant now = Instant.now();

        jiraIssueSyncService.syncProjectIssues(project.getId());

        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        projectRepository.save(project);

        return jiraHealthService.getHealthOverview(project.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingChannelDto> getProjectMeetingChannels(String authenticatedUserId, String projectId) {
        return meetingChannelService.listForSupervisor(authenticatedUserId, projectId);
    }

    @Override
    @Transactional
    public MeetingChannelDto addProjectMeetingChannel(
        String authenticatedUserId,
        String projectId,
        CreateMeetingChannelRequest request
    ) {
        return meetingChannelService.createAsSupervisor(authenticatedUserId, projectId, request);
    }

    @Override
    @Transactional
    public MeetingChannelDto updateProjectMeetingChannel(
        String authenticatedUserId,
        String projectId,
        String channelId,
        UpdateMeetingChannelRequest request
    ) {
        return meetingChannelService.updateAsSupervisor(authenticatedUserId, projectId, channelId, request);
    }

    @Override
    @Transactional
    public void deleteProjectMeetingChannel(String authenticatedUserId, String projectId, String channelId) {
        meetingChannelService.deleteAsSupervisor(authenticatedUserId, projectId, channelId);
    }

    @Override
    @Transactional
    public MeetingChannelDto approveProjectMeetingChannel(
        String authenticatedUserId,
        String projectId,
        String channelId
    ) {
        return meetingChannelService.approveAsSupervisor(authenticatedUserId, projectId, channelId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingRecordDto> getProjectMeetingRecords(String authenticatedUserId, String projectId) {
        return meetingRecordService.listForSupervisor(authenticatedUserId, projectId);
    }

    @Override
    @Transactional
    public MeetingRecordDto addProjectMeetingRecord(
        String authenticatedUserId,
        String projectId,
        CreateMeetingRecordRequest request
    ) {
        return meetingRecordService.createAsSupervisor(authenticatedUserId, projectId, request);
    }

    @Override
    @Transactional
    public MeetingRecordDto updateProjectMeetingRecord(
        String authenticatedUserId,
        String projectId,
        String recordId,
        UpdateMeetingRecordRequest request
    ) {
        return meetingRecordService.updateAsSupervisor(authenticatedUserId, projectId, recordId, request);
    }

    @Override
    @Transactional
    public void deleteProjectMeetingRecord(String authenticatedUserId, String projectId, String recordId) {
        meetingRecordService.deleteAsSupervisor(authenticatedUserId, projectId, recordId);
    }

    @Override
    @Transactional
    public MeetingRecordDto approveProjectMeetingRecord(
        String authenticatedUserId,
        String projectId,
        String recordId
    ) {
        return meetingRecordService.approveAsSupervisor(authenticatedUserId, projectId, recordId);
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
                projectMemberRepository.countByProjectId(project.getId()));
    }

    private SupervisorDashboardDto.ProjectItem toDashboardProjectItem(
            Project project,
            LocalDate effectiveMilestoneDate,
            String jiraHealthIndicator) {
        SupervisorDashboardDto.ProjectItem item = new SupervisorDashboardDto.ProjectItem(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                effectiveMilestoneDate,
                project.getLastActivityAt(),
                project.getProgressPercent());
        item.setJiraHealthIndicator(jiraHealthIndicator);
        return item;
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

    private SupervisorProjectDetailDto.Milestone toDetailMilestone(
            ProjectMilestone milestone,
            MilestonePolicyEngine.MilestoneSignal signal) {
        SupervisorProjectDetailDto.Milestone milestoneDto = new SupervisorProjectDetailDto.Milestone(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getDueDate(),
                milestone.getStatus(),
                milestone.getSequenceNo());
        milestoneDto.setIsOverdue(signal != null && signal.isOverdue());
        milestoneDto.setDaysOverdue(signal == null ? 0 : signal.daysOverdue());
        milestoneDto.setIsChronologyViolation(signal != null && signal.isChronologyViolation());
        return milestoneDto;
    }

    private UUID parseProjectId(String projectId) {
        return EntityIdParser.parseOrNotFound(projectId);
    }

    private UUID parseLinkedRepositoryId(String linkedRepositoryId) {
        return EntityIdParser.parseOrNull(linkedRepositoryId, "linkedRepositoryId");
    }

    private UUID parseMilestoneId(String milestoneId) {
        return EntityIdParser.parseOrNotFound(milestoneId);
    }

    private String normalizeJiraScope(String configuredScope) {
        String scope = NormalizationUtils.defaultIfBlank(NormalizationUtils.trimToNull(configuredScope), DEFAULT_JIRA_SCOPE);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String token : scope.split("\\s+")) {
            String trimmed = NormalizationUtils.trimToNull(token);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        normalized.add(REQUIRED_JIRA_SCOPE);
        return String.join(" ", normalized);
    }

    private String urlencode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    private record MilestoneDetailView(
            List<SupervisorProjectDetailDto.Milestone> milestones,
            SupervisorProjectDetailDto.MilestoneInsights insights,
            LocalDate milestoneDate) {
    }

    private String validateLifecycleStatus(String rawStatus) {
        String lifecycleStatus = rawStatus.trim().toUpperCase();
        if (!ALLOWED_LIFECYCLE_STATUSES.contains(lifecycleStatus)) {
            throw new ValidationException("lifecycleStatus", "Lifecycle status is invalid.");
        }
        return lifecycleStatus;
    }

    private String buildAtlassianTokenExchangeIssue(RestClientResponseException exception) {
        String body = NormalizationUtils.trimToNull(exception.getResponseBodyAsString());
        if (body != null) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(body);
                String error = NormalizationUtils.trimToNull(node.path("error").asText(null));
                String errorDescription = NormalizationUtils.trimToNull(node.path("error_description").asText(null));
                if (error != null && errorDescription != null) {
                    return "Atlassian token exchange failed: " + error + " (" + errorDescription + ")";
                }
                if (error != null) {
                    return "Atlassian token exchange failed: " + error;
                }
            } catch (Exception ignored) {
            }
        }
        return "Atlassian token exchange failed (HTTP " + exception.getStatusCode().value() + ").";
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Base64(String raw) {
        return Base64.getEncoder()
            .encodeToString(sha256Bytes((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8)));
    }


    private byte[] sha256Bytes(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }
}
