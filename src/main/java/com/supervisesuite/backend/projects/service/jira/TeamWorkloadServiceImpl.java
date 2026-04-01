package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.TeamWorkloadResponseDto;
import com.supervisesuite.backend.projects.dto.TeamWorkloadStudentDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TeamWorkloadServiceImpl implements TeamWorkloadService {

    private static final String STATUS_CATEGORY_KEY_DONE = "done";
    private static final String STATUS_CATEGORY_KEY_IN_PROGRESS = "indeterminate";
        private static final int STALE_OVERDUE_DAYS = 7;
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
                if (rawIssues == null || rawIssues.isEmpty()) {
                        return new TeamWorkloadResponseDto(List.of(), 0, false, null, false);
                }

        List<JiraIssueData> resolvedIssues = jiraAssigneeResolver.resolveAssigneeWorkUnits(rawIssues);
                LocalDate today = LocalDate.now();

        int unassignedIssues = (int) resolvedIssues.stream()
                .filter(issue -> !hasText(issue.getAssigneeAccountId()))
                .count();

        Map<String, List<JiraIssueData>> issuesByAssignee = resolvedIssues.stream()
                .filter(issue -> hasText(issue.getAssigneeAccountId()))
                .collect(Collectors.groupingBy(JiraIssueData::getAssigneeAccountId));

        List<TeamWorkloadStudentDto> students = issuesByAssignee.entrySet().stream()
                .map(entry -> toStudentWorkload(entry.getKey(), entry.getValue(), today))
                .sorted(Comparator.comparingInt(TeamWorkloadStudentDto::getAssigned).reversed())
                .toList();

        ImbalanceResult imbalance = computeImbalance(students);
        boolean dueDateAvailable = rawIssues.stream().anyMatch(issue -> issue.getDueDate() != null);

        return new TeamWorkloadResponseDto(
                students,
                unassignedIssues,
                imbalance.detected(),
                imbalance.message(),
                dueDateAvailable);
    }

    private TeamWorkloadStudentDto toStudentWorkload(String accountId, List<JiraIssueData> issues, LocalDate today) {
        int assigned = issues.size();
        int completed = (int) issues.stream().filter(issue -> STATUS_CATEGORY_KEY_DONE.equals(issue.getStatusCategoryKey())).count();
        int inProgress = (int) issues.stream()
                .filter(issue -> STATUS_CATEGORY_KEY_IN_PROGRESS.equals(issue.getStatusCategoryKey()))
                .count();
        int overdue = (int) issues.stream().filter(issue -> isIssueOverdue(issue, today)).count();

        double storyPointsAssigned = issues.stream()
                .mapToDouble(issue -> issue.getStoryPoints() == null ? 0.0 : issue.getStoryPoints())
                .sum();

        double storyPointsCompleted = issues.stream()
                .filter(issue -> STATUS_CATEGORY_KEY_DONE.equals(issue.getStatusCategoryKey()))
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
                                overdue,
                storyPointsAssigned,
                storyPointsCompleted,
                lastActiveDate,
                completionRate);
    }

        private boolean isIssueOverdue(JiraIssueData issue, LocalDate today) {
                if (STATUS_CATEGORY_KEY_DONE.equals(issue.getStatusCategoryKey())) {
                        return false;
                }

                LocalDate dueDate = issue.getDueDate();
                if (dueDate != null) {
                        return dueDate.isBefore(today);
                }

                Instant lastUpdated = issue.getLastUpdated();
                if (lastUpdated == null) {
                        return false;
                }

                LocalDate staleCutoff = today.minusDays(STALE_OVERDUE_DAYS);
                LocalDate lastUpdatedDate = lastUpdated.atZone(ZoneOffset.UTC).toLocalDate();
                return lastUpdatedDate.isBefore(staleCutoff);
        }

        private ImbalanceResult computeImbalance(List<TeamWorkloadStudentDto> students) {
                List<TeamWorkloadStudentDto> activeStudents = students.stream()
                                .filter(student -> student.getAssigned() > 0)
                                .toList();

                if (activeStudents.size() < 2) {
                        return new ImbalanceResult(false, null);
                }

                TeamWorkloadStudentDto maxStudent = activeStudents.stream()
                                .max(Comparator.comparingInt(TeamWorkloadStudentDto::getOpenIssues))
                                .orElse(null);
                TeamWorkloadStudentDto minStudent = activeStudents.stream()
                                .min(Comparator.comparingInt(TeamWorkloadStudentDto::getOpenIssues))
                                .orElse(null);

                if (maxStudent == null || minStudent == null) {
                        return new ImbalanceResult(false, null);
                }

                boolean detected = maxStudent.getOpenIssues() > 3 * minStudent.getOpenIssues()
                                && maxStudent.getOpenIssues() >= 3;
                if (!detected) {
                        return new ImbalanceResult(false, null);
                }

                String message = String.format(
                                "%s has significantly more open issues than %s (%d vs %d)",
                                maxStudent.getDisplayName(),
                                minStudent.getDisplayName(),
                                maxStudent.getOpenIssues(),
                                minStudent.getOpenIssues());
                return new ImbalanceResult(true, message);
        }

        private static record ImbalanceResult(boolean detected, String message) {
        }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
