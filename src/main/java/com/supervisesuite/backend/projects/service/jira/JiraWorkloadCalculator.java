package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Pure domain component responsible for reducing raw Jira issue collections into 
 * consolidated member workload statistics.
 */
@Component
class JiraWorkloadCalculator {

    private final JiraHealthClassifier classifier;

    JiraWorkloadCalculator(JiraHealthClassifier classifier) {
        this.classifier = classifier;
    }

    public Result calculate(List<ProjectJiraIssue> issues, boolean dueDateAvailable, LocalDate today, Instant overdueThreshold) {
        Set<String> parentKeys = new HashSet<>();
        for (ProjectJiraIssue issue : issues) {
            String parentKey = issue.getParentKey();
            if (parentKey != null && !parentKey.isBlank()) {
                parentKeys.add(parentKey);
            }
        }

        Map<String, MemberAccumulator> accumulators = new HashMap<>();
        int unassignedCount = 0;

        for (ProjectJiraIssue issue : issues) {
            boolean isParent = parentKeys.contains(issue.getIssueKey());
            String accountId = issue.getAssigneeAccountId();

            if (accountId == null || accountId.isBlank()) {
                if (!isParent) {
                    unassignedCount++;
                }
                continue;
            }

            MemberAccumulator acc = accumulators.computeIfAbsent(
                    accountId, 
                    id -> new MemberAccumulator(id, issue.getAssigneeDisplayName()));
                    
            if (isParent) {
                acc.accumulateStoryPoints(issue, classifier);
            } else {
                acc.accumulate(issue, classifier, dueDateAvailable, today, overdueThreshold);
            }
        }

        List<JiraWorkloadDto.MemberRow> sortedRows = accumulators.values().stream()
                .map(MemberAccumulator::toMemberRow)
                .sorted(Comparator.comparingInt(JiraWorkloadDto.MemberRow::openIssues).reversed())
                .toList();

        return new Result(sortedRows, unassignedCount);
    }

    public record Result(List<JiraWorkloadDto.MemberRow> members, int unassignedCount) {}

    // Inner stateful accumulator class moved from Service to Domain Calculator
    private static final class MemberAccumulator {
        private final String accountId;
        private final String displayName;

        private int assigned;
        private int completed;
        private int inProgress;
        private int overdue;
        private BigDecimal storyPointsAssigned;
        private BigDecimal storyPointsCompleted;
        private boolean storyPointsSeen;
        private Instant lastActiveDate;
        private final Map<String, Integer> issueTypeCounts = new HashMap<>();

        MemberAccumulator(String accountId, String displayName) {
            this.accountId = accountId;
            this.displayName = displayName != null ? displayName : accountId;
        }

        void accumulate(ProjectJiraIssue issue, JiraHealthClassifier classifier, boolean dueDateAvailable, LocalDate today, Instant overdueThreshold) {
            assigned++;
            recordIssueType(issue);
            
            String statusCategory = issue.getStatusCategoryKey();
            if (classifier.isDoneStatus(statusCategory)) {
                completed++;
            } else {
                if (classifier.isInProgressStatus(statusCategory)) {
                    inProgress++;
                }
                if (dueDateAvailable) {
                    LocalDate dueDate = issue.getDueDate();
                    if (dueDate != null && dueDate.isBefore(today)) {
                        overdue++;
                    }
                } else {
                    Instant lastUpdated = issue.getJiraUpdatedAt();
                    if (lastUpdated != null && lastUpdated.isBefore(overdueThreshold)) {
                        overdue++;
                    }
                }
            }
            accumulateStoryPoints(issue, classifier);
            updateLastActive(issue);
        }

        void accumulateStoryPoints(ProjectJiraIssue issue, JiraHealthClassifier classifier) {
            BigDecimal sp = issue.getStoryPoints();
            if (sp != null) {
                storyPointsSeen = true;
                storyPointsAssigned = storyPointsAssigned == null ? sp : storyPointsAssigned.add(sp);
                if (classifier.isDoneStatus(issue.getStatusCategoryKey())) {
                    storyPointsCompleted = storyPointsCompleted == null ? sp : storyPointsCompleted.add(sp);
                }
            }
            recordIssueType(issue);
            updateLastActive(issue);
        }

        private void recordIssueType(ProjectJiraIssue issue) {
            String type = issue.getIssueType();
            String key = (type == null || type.isBlank()) ? "Unknown" : type;
            issueTypeCounts.merge(key, 1, Integer::sum);
        }

        private void updateLastActive(ProjectJiraIssue issue) {
            Instant updatedAt = issue.getJiraUpdatedAt();
            if (updatedAt != null && (lastActiveDate == null || updatedAt.isAfter(lastActiveDate))) {
                lastActiveDate = updatedAt;
            }
        }

        JiraWorkloadDto.MemberRow toMemberRow() {
            int openIssues = assigned - completed;
            double completionRate = assigned == 0 ? 0.0 : (double) completed / assigned * 100.0;
            return new JiraWorkloadDto.MemberRow(accountId, displayName, assigned, completed, inProgress, overdue, openIssues,
                    storyPointsSeen ? storyPointsAssigned : null, storyPointsSeen ? storyPointsCompleted : null,
                    completionRate, lastActiveDate, Map.copyOf(issueTypeCounts));
        }
    }
}
