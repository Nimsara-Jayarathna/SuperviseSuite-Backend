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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TeamWorkloadServiceImpl implements TeamWorkloadService {

        private static final String STATUS_CATEGORY_DONE_KEY = "done";
        private static final String STATUS_CATEGORY_IN_PROGRESS_KEY = "indeterminate";
        private static final int STALE_OVERDUE_DAYS = 7;
        private static final int IMBALANCE_MULTIPLIER = 3;
        private static final int MIN_ASSIGNED_FOR_IMBALANCE = 3;
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

        int unassignedIssues = countUnassignedIssues(resolvedIssues);
        List<TeamWorkloadStudentDto> students = buildStudentWorkloads(resolvedIssues, today);

        ImbalanceResult imbalance = computeImbalance(students);
        boolean dueDateAvailable = hasAnyIssueDueDate(rawIssues);

        return new TeamWorkloadResponseDto(
                students,
                unassignedIssues,
                imbalance.detected(),
                imbalance.message(),
                dueDateAvailable);
    }

    private int countUnassignedIssues(List<JiraIssueData> resolvedIssues) {
        return (int) resolvedIssues.stream()
                .filter(issue -> !hasText(issue.getAssigneeAccountId()))
                .count();
    }

    private List<TeamWorkloadStudentDto> buildStudentWorkloads(List<JiraIssueData> resolvedIssues, LocalDate today) {
        Map<String, List<JiraIssueData>> issuesByAssignee = resolvedIssues.stream()
                .filter(issue -> hasText(issue.getAssigneeAccountId()))
                .collect(Collectors.groupingBy(JiraIssueData::getAssigneeAccountId));

        return issuesByAssignee.entrySet().stream()
                .map(entry -> toStudentWorkload(entry.getKey(), entry.getValue(), today))
                .sorted(Comparator.comparingInt(this::openIssueCount).reversed())
                .toList();
    }

    private boolean hasAnyIssueDueDate(List<JiraIssueData> rawIssues) {
        return rawIssues.stream().anyMatch(issue -> issue.getDueDate() != null);
    }

    private TeamWorkloadStudentDto toStudentWorkload(String accountId, List<JiraIssueData> issues, LocalDate today) {
        int assigned = issues.size();
        int completed = (int) issues.stream().filter(this::isDoneCategory).count();
        int inProgress = (int) issues.stream()
                .filter(this::isInProgressCategory)
                .count();
        int overdue = (int) issues.stream().filter(issue -> isIssueOverdue(issue, today)).count();

        double storyPointsAssigned = sumStoryPoints(issues);

        double storyPointsCompleted = issues.stream()
                .filter(this::isDoneCategory)
                .mapToDouble(issue -> toStoryPoints(issue.getStoryPoints()))
                .sum();

        String displayName = resolveDisplayName(issues);
        String lastActiveDate = resolveLastActiveDate(issues);

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

    private double sumStoryPoints(List<JiraIssueData> issues) {
        return issues.stream()
                .mapToDouble(issue -> toStoryPoints(issue.getStoryPoints()))
                .sum();
    }

    private double toStoryPoints(Double storyPoints) {
        return storyPoints == null ? 0.0 : storyPoints;
    }

    private String resolveDisplayName(List<JiraIssueData> issues) {
        return issues.stream()
                .map(JiraIssueData::getAssigneeDisplayName)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private String resolveLastActiveDate(List<JiraIssueData> issues) {
        Instant lastActiveInstant = issues.stream()
                .map(JiraIssueData::getLastUpdated)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return lastActiveInstant == null
                ? null
                : DATE_FORMATTER.format(lastActiveInstant.atZone(ZoneOffset.UTC).toLocalDate());
    }

    private boolean isIssueOverdue(JiraIssueData issue, LocalDate today) {
        if (isDoneCategory(issue)) {
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
                .filter(student -> openIssueCount(student) > 0)
                .toList();

        if (activeStudents.size() < 2) {
            return new ImbalanceResult(false, null);
        }

        TeamWorkloadStudentDto maxStudent = activeStudents.stream()
                .max(Comparator.comparingInt(this::openIssueCount))
                .orElse(null);
        TeamWorkloadStudentDto minStudent = activeStudents.stream()
                .min(Comparator.comparingInt(this::openIssueCount))
                .orElse(null);

        if (maxStudent == null || minStudent == null) {
            return new ImbalanceResult(false, null);
        }

        int maxOpen = openIssueCount(maxStudent);
        int minOpen = openIssueCount(minStudent);

        boolean exceedsRatio = minOpen == 0 ? maxOpen > 0 : maxOpen > IMBALANCE_MULTIPLIER * minOpen;
        boolean detected = exceedsRatio && maxStudent.getAssigned() >= MIN_ASSIGNED_FOR_IMBALANCE;
        if (!detected) {
            return new ImbalanceResult(false, null);
        }

        String message = String.format(
                "%s has 3x more open issues than %s",
                maxStudent.getDisplayName(),
                minStudent.getDisplayName());
        return new ImbalanceResult(true, message);
    }

    private int openIssueCount(TeamWorkloadStudentDto student) {
        return Math.max(0, student.getAssigned() - student.getCompleted());
    }

    private boolean isDoneCategory(JiraIssueData issue) {
        String normalized = normalizeCategory(issue == null ? null : issue.getStatusCategory());
        return STATUS_CATEGORY_DONE_KEY.equals(normalized);
    }

    private boolean isInProgressCategory(JiraIssueData issue) {
        String normalized = normalizeCategory(issue == null ? null : issue.getStatusCategory());
        return STATUS_CATEGORY_IN_PROGRESS_KEY.equals(normalized) || "in progress".equals(normalized);
    }

    private String normalizeCategory(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static record ImbalanceResult(boolean detected, String message) {
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
