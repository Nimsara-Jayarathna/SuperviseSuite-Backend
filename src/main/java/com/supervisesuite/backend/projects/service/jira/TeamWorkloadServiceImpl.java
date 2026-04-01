package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.TeamWorkloadResponseDto;
import com.supervisesuite.backend.projects.dto.TeamWorkloadStudentDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TeamWorkloadServiceImpl implements TeamWorkloadService {

    private static final String STATUS_DONE = "Done";
    private static final String STATUS_IN_PROGRESS = "In Progress";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JiraIssueClient jiraIssueClient;
    private final JiraAssigneeResolver jiraAssigneeResolver;

    public TeamWorkloadServiceImpl(JiraIssueClient jiraIssueClient, JiraAssigneeResolver jiraAssigneeResolver) {
        this.jiraIssueClient = jiraIssueClient;
        this.jiraAssigneeResolver = jiraAssigneeResolver;
    }

    @Override
    public TeamWorkloadResponseDto computeWorkload(ProjectJiraIntegration integration) {
        List<JiraIssueData> rawIssues = jiraIssueClient.fetchProjectIssues(integration);
        List<JiraIssueData> resolvedIssues = jiraAssigneeResolver.resolveAssigneeWorkUnits(rawIssues);

        int unassignedIssues = (int) resolvedIssues.stream()
                .filter(issue -> !hasText(issue.getAssigneeAccountId()))
                .count();

        Map<String, List<JiraIssueData>> issuesByAssignee = resolvedIssues.stream()
                .filter(issue -> hasText(issue.getAssigneeAccountId()))
                .collect(Collectors.groupingBy(JiraIssueData::getAssigneeAccountId));

        List<TeamWorkloadStudentDto> students = issuesByAssignee.entrySet().stream()
                .map(entry -> toStudentWorkload(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(TeamWorkloadStudentDto::getAssigned).reversed())
                .toList();

        boolean dueDateAvailable = rawIssues.stream().anyMatch(issue -> issue.getDueDate() != null);

        return new TeamWorkloadResponseDto(
                students,
                unassignedIssues,
                false,
                null,
                dueDateAvailable);
    }

    private TeamWorkloadStudentDto toStudentWorkload(String accountId, List<JiraIssueData> issues) {
        int assigned = issues.size();
        int completed = (int) issues.stream().filter(issue -> STATUS_DONE.equals(issue.getStatusCategory())).count();
        int inProgress = (int) issues.stream()
                .filter(issue -> STATUS_IN_PROGRESS.equals(issue.getStatusCategory()))
                .count();

        double storyPointsAssigned = issues.stream()
                .mapToDouble(issue -> issue.getStoryPoints() == null ? 0.0 : issue.getStoryPoints())
                .sum();

        double storyPointsCompleted = issues.stream()
                .filter(issue -> STATUS_DONE.equals(issue.getStatusCategory()))
                .mapToDouble(issue -> issue.getStoryPoints() == null ? 0.0 : issue.getStoryPoints())
                .sum();

        String displayName = issues.stream()
                .map(JiraIssueData::getAssigneeDisplayName)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);

        Instant lastActiveInstant = issues.stream()
                .map(JiraIssueData::getLastUpdated)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        String lastActiveDate = lastActiveInstant == null
                ? null
                : DATE_FORMATTER.format(lastActiveInstant.atZone(ZoneOffset.UTC).toLocalDate());

        int completionRate = assigned > 0 ? (completed * 100 / assigned) : 0;

        return new TeamWorkloadStudentDto(
                displayName,
                accountId,
                assigned,
                completed,
                inProgress,
                0,
                storyPointsAssigned,
                storyPointsCompleted,
                lastActiveDate,
                completionRate);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
