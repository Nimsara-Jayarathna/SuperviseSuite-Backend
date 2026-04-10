package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Domain component responsible for reducing a flat list of Jira issues into health statistics.
 */
@Component
class JiraHealthMetricsAggregator {

    private final JiraHealthClassifier classifier;

    JiraHealthMetricsAggregator(JiraHealthClassifier classifier) {
        this.classifier = classifier;
    }

    public Result aggregate(List<ProjectJiraIssue> issues, LocalDate today) {
        int doneCount = 0;
        int toDoCount = 0;
        int inProgressCount = 0;
        int overdueCount = 0;
        int highPriorityOpen = 0;
        int openBugCount = 0;

        for (ProjectJiraIssue issue : issues) {
            String statusKey = issue.getStatusCategoryKey();
            boolean isDone = classifier.isDoneStatus(statusKey);

            if (isDone) {
                doneCount++;
            } else {
                // Status breakdown buckets
                if (classifier.isToDoStatus(statusKey)) {
                    toDoCount++;
                } else if (classifier.isInProgressStatus(statusKey)) {
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
                if (classifier.isHighPriority(priority)) {
                    highPriorityOpen++;
                }

                // Open bugs
                if (classifier.isBugType(issue.getIssueType())) {
                    openBugCount++;
                }
            }
        }

        int total = issues.size();
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

        return new Result(
                completionPercent,
                openIssues,
                overdueCount,
                highPriorityOpen,
                new JiraHealthDto.StatusBreakdown(toDoCount, inProgressCount, doneCount),
                typeDistribution,
                bugRatio);
    }

    public record Result(
            double completionPercent,
            int openIssues,
            int overdueCount,
            int highPriorityOpen,
            JiraHealthDto.StatusBreakdown statusBreakdown,
            List<JiraHealthDto.TypeCount> typeDistribution,
            double bugRatio) {}
}
