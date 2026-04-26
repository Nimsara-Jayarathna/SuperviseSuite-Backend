package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.memberships.entity.ProjectMember;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.dto.ProjectGitHubAccessMetadata;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoriesDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.service.ProjectService;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.service.milestones.MilestonePolicyEngine;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorDashboardDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectSummaryDto;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SupervisorProjectDtoMapper {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final ProjectService projectService;
    private final RepositoryLinkService repositoryLinkService;
    private final ProjectJiraIntegrationRepository projectJiraIntegrationRepository;
    private final MilestonePolicyEngine milestonePolicyEngine;

    SupervisorProjectDtoMapper(
            UserRepository userRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectMilestoneRepository projectMilestoneRepository,
            ProjectService projectService,
            RepositoryLinkService repositoryLinkService,
            ProjectJiraIntegrationRepository projectJiraIntegrationRepository,
            MilestonePolicyEngine milestonePolicyEngine) {
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.projectService = projectService;
        this.repositoryLinkService = repositoryLinkService;
        this.projectJiraIntegrationRepository = projectJiraIntegrationRepository;
        this.milestonePolicyEngine = milestonePolicyEngine;
    }

    SupervisorProjectDetailDto toProjectDetail(Project project) {
        ProjectGitHubRepositoriesDto githubRepositories = null;
        try {
            githubRepositories = repositoryLinkService.getProjectRepositories(
                    project.getId().toString(),
                    project.getSupervisor().getId().toString());
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

    StudentSearchResultDto toStudentSearchResult(User user) {
        return new StudentSearchResultDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRegistrationNumber());
    }

    CreateSupervisorProjectResponse.StudentAssignment toStudentAssignment(User user) {
        return new CreateSupervisorProjectResponse.StudentAssignment(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRegistrationNumber());
    }

    CreateSupervisorProjectResponse.StudentAssignment toCreateLeaderAssignment(UUID leaderUserId) {
        if (leaderUserId == null) {
            return null;
        }
        return userRepository.findById(leaderUserId)
                .map(this::toStudentAssignment)
                .orElse(null);
    }

    CreateSupervisorProjectResponse.Milestone toCreateMilestone(ProjectMilestone milestone) {
        return new CreateSupervisorProjectResponse.Milestone(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getDueDate(),
                milestone.getStatus(),
                milestone.getSequenceNo());
    }

    SupervisorProjectSummaryDto toProjectSummary(Project project) {
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

    SupervisorDashboardDto.ProjectItem toDashboardProjectItem(
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

    private record MilestoneDetailView(
            List<SupervisorProjectDetailDto.Milestone> milestones,
            SupervisorProjectDetailDto.MilestoneInsights insights,
            LocalDate milestoneDate) {
    }
}
