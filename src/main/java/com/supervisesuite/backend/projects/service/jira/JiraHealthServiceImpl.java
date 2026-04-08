package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JiraHealthServiceImpl implements JiraHealthService {

    private final ProjectJiraIssueRepository jiraIssueRepository;
    private final JiraHealthClassifier jiraHealthClassifier;

    JiraHealthServiceImpl(
            ProjectJiraIssueRepository jiraIssueRepository,
            JiraHealthClassifier jiraHealthClassifier) {
        this.jiraIssueRepository = jiraIssueRepository;
        this.jiraHealthClassifier = jiraHealthClassifier;
    }

    @Override
    @Transactional(readOnly = true)
    public JiraHealthDto getHealthOverview(UUID projectId) {
        List<ProjectJiraIssue> issues = jiraIssueRepository.findAllByProjectId(projectId);

        if (issues.isEmpty()) {
            return new JiraHealthDto(
                    0.0,
                    0,
                    0,
                    0,
                    new JiraHealthDto.StatusBreakdown(0, 0, 0),
                    List.of(),
                    0.0,
                    null);
        }

        int total = issues.size();
        LocalDate today = LocalDate.now();

        int doneCount          = 0;
        int toDoCount          = 0;
        int inProgressCount    = 0;
        int overdueCount       = 0;
        int highPriorityOpen   = 0;
        int openBugCount       = 0;

        for (ProjectJiraIssue issue : issues) {
            String statusKey = issue.getStatusCategoryKey();
            boolean isDone = jiraHealthClassifier.isDoneStatus(statusKey);

            if (isDone) {
                doneCount++;
            } else {
                // Status breakdown buckets
                if (jiraHealthClassifier.isToDoStatus(statusKey)) {
                    toDoCount++;
                } else if (jiraHealthClassifier.isInProgressStatus(statusKey)) {
                    inProgressCount++;
                } else {
                    // Unknown non-done category still belongs to open work.
                    toDoCount++;
                }

                // Overdue: has a due date and that date is in the past
                LocalDate dueDate = issue.getDueDate();
                if (dueDate != null && dueDate.isBefore(today)) {
                    overdueCount++;
                }

                // High / Highest priority open issues
                String priority = issue.getPriorityName();
                if (jiraHealthClassifier.isHighPriority(priority)) {
                    highPriorityOpen++;
                }

                // Open bugs
                if (jiraHealthClassifier.isBugType(issue.getIssueType())) {
                    openBugCount++;
                }
            }
        }

        int openIssues = total - doneCount;
        double completionPercent = (double) doneCount / total * 100.0;
        double bugRatio = openIssues == 0 ? 0.0 : (double) openBugCount / openIssues * 100.0;

        List<JiraHealthDto.TypeCount> typeDistribution = issues.stream()
                .filter(i -> i.getIssueType() != null)
                .collect(Collectors.groupingBy(ProjectJiraIssue::getIssueType, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new JiraHealthDto.TypeCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(JiraHealthDto.TypeCount::count).reversed())
                .collect(Collectors.toList());

        Instant lastSyncedAt = jiraIssueRepository
                .findMaxSyncedAtByProjectId(projectId)
                .orElse(null);

        return new JiraHealthDto(
                completionPercent,
                openIssues,
                overdueCount,
                highPriorityOpen,
                new JiraHealthDto.StatusBreakdown(toDoCount, inProgressCount, doneCount),
                typeDistribution,
                bugRatio,
                lastSyncedAt);
    }
}
