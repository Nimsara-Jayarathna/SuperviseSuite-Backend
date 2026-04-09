package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link JiraWorkloadService}.
 *
 * <h2>Algorithm</h2>
 * All computation is performed in-memory over the synced {@code project_jira_issues}
 * cache — a single DB round-trip per request with O(n) reduction:
 * <ol>
 *   <li>Load all issues for the project.</li>
 *   <li>Build the set of parent issue keys (issues that have children).</li>
 *   <li>Determine whether due-date overdue detection is available.</li>
 *   <li>Attribute each leaf issue to its assignee (or unassigned bucket).</li>
 *   <li>Sort member rows by open issues descending.</li>
 *   <li>Detect workload imbalance.</li>
 *   <li>Assemble and return the {@link JiraWorkloadDto}.</li>
 * </ol>
 *
 * <h2>Subtask attribution</h2>
 * A parent issue (one whose {@code issueKey} appears as another issue's {@code parentKey})
 * is excluded from direct attribution. Work is credited to its subtask assignees only,
 * preventing double-counting when a team lead owns the story but subtasks are
 * distributed to contributors.
 */
@Service
class JiraWorkloadServiceImpl implements JiraWorkloadService {

    /** Imbalance threshold: outlier must have at least this many open issues. */
    private static final int IMBALANCE_MIN_OPEN = 3;

    /** Imbalance threshold: outlier must have strictly more than this multiple of the minimum. */
    private static final int IMBALANCE_MULTIPLIER = 3;

    /**
     * Overdue fallback: issues with no due date are considered overdue when last updated
     * more than this many days ago and they are still open.
     */
    private static final long OVERDUE_FALLBACK_DAYS = 7;

    private final ProjectJiraIssueRepository jiraIssueRepository;
    private final JiraHealthClassifier classifier;

    JiraWorkloadServiceImpl(
            ProjectJiraIssueRepository jiraIssueRepository,
            JiraHealthClassifier classifier) {
        this.jiraIssueRepository = jiraIssueRepository;
        this.classifier = classifier;
    }

    @Override
    @Transactional(readOnly = true)
    public JiraWorkloadDto getWorkload(UUID projectId) {
        List<ProjectJiraIssue> issues = jiraIssueRepository.findAllForWorkloadByProjectId(projectId);

        if (issues.isEmpty()) {
            return emptyWorkload();
        }

        // Step 1 — Identify parent issue keys (issues that have at least one child).
        // An issue is a "parent" when its issueKey appears as another issue's parentKey.
        Set<String> parentKeys = buildParentKeySet(issues);

        // Step 2 — Determine overdue detection mode.
        // When any issue in the project has a dueDate, use dueDate < today for all overdue
        // checks. Otherwise fall back to activity-recency (jiraUpdatedAt > 7 days ago).
        boolean dueDateAvailable = jiraIssueRepository
                .existsByProjectIdAndDueDateIsNotNull(projectId);
        LocalDate today = LocalDate.now();
        Instant overdueThreshold = Instant.now().minus(OVERDUE_FALLBACK_DAYS, ChronoUnit.DAYS);

        // Step 3 — Reduction: attribute each leaf issue to a member accumulator.
        Map<String, MemberAccumulator> accumulatorsByAccountId = new HashMap<>();
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

            MemberAccumulator accumulator = accumulatorsByAccountId.computeIfAbsent(
                    accountId,
                    id -> new MemberAccumulator(id, issue.getAssigneeDisplayName()));

            if (isParent) {
                // Parent issues (e.g. User Stories) are skipped for issue counting to avoid
                // double-counting their subtasks, but we MUST attribute their Story Points
                // to their assignee because SPs are typically estimated at the parent level.
                accumulator.accumulateStoryPoints(issue, classifier);
            } else {
                accumulator.accumulate(issue, classifier, dueDateAvailable, today, overdueThreshold);
            }
        }

        // Step 4 — Build sorted member rows: open issues (assigned - completed) descending.
        List<JiraWorkloadDto.MemberRow> members = accumulatorsByAccountId.values().stream()
                .map(MemberAccumulator::toMemberRow)
                .sorted(Comparator.comparingInt(JiraWorkloadDto.MemberRow::openIssues).reversed())
                .collect(java.util.stream.Collectors.toList());

        // Step 5 — Detect workload imbalance.
        ImbalanceResult imbalance = detectImbalance(members);

        return new JiraWorkloadDto(
                members,
                unassignedCount,
                dueDateAvailable,
                imbalance.detected(),
                imbalance.message());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the set of issue keys that appear as the {@code parentKey} of at least
     * one other issue in the same batch. These are the "parent" issues to be excluded
     * from direct attribution.
     */
    private static Set<String> buildParentKeySet(List<ProjectJiraIssue> issues) {
        Set<String> parents = new HashSet<>();
        for (ProjectJiraIssue issue : issues) {
            String parentKey = issue.getParentKey();
            if (parentKey != null && !parentKey.isBlank()) {
                parents.add(parentKey);
            }
        }
        return parents;
    }

    /**
     * Applies the imbalance detection rule:
     * {@code maxOpen > MULTIPLIER × minOpen AND maxOpen >= IMBALANCE_MIN_OPEN}.
     *
     * <p>A single-member team or a team where everyone has zero open issues cannot
     * trigger the imbalance alert — both conditions must be simultaneously true.</p>
     */
    private static ImbalanceResult detectImbalance(List<JiraWorkloadDto.MemberRow> members) {
        if (members.size() < 2) {
            return ImbalanceResult.none();
        }

        int maxOpen = members.stream()
                .mapToInt(JiraWorkloadDto.MemberRow::openIssues)
                .max()
                .orElse(0);
        int minOpen = members.stream()
                .mapToInt(JiraWorkloadDto.MemberRow::openIssues)
                .min()
                .orElse(0);

        if (maxOpen < IMBALANCE_MIN_OPEN || maxOpen <= IMBALANCE_MULTIPLIER * minOpen) {
            return ImbalanceResult.none();
        }

        // Identify the outlier (most open) and the least-burdened member (least open).
        JiraWorkloadDto.MemberRow outlier = members.stream()
                .max(Comparator.comparingInt(JiraWorkloadDto.MemberRow::openIssues))
                .orElseThrow();
        JiraWorkloadDto.MemberRow leastBurdened = members.stream()
                .min(Comparator.comparingInt(JiraWorkloadDto.MemberRow::openIssues))
                .orElseThrow();

        String message = "%s has %dx more open issues than %s".formatted(
                outlier.displayName(),
                IMBALANCE_MULTIPLIER,
                leastBurdened.displayName());

        return new ImbalanceResult(true, message);
    }

    /** Returns a valid empty workload when no issues exist for the project. */
    private static JiraWorkloadDto emptyWorkload() {
        return new JiraWorkloadDto(List.of(), 0, false, false, null);
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Mutable accumulator for a single member's workload metrics.
     *
     * <p>Follows the Accumulator / Builder pattern: built up incrementally during
     * the single O(n) reduction pass, then converted to an immutable
     * {@link JiraWorkloadDto.MemberRow} via {@link #toMemberRow()}.</p>
     *
     * <p>Package-private visibility is intentional — only {@link JiraWorkloadServiceImpl}
     * needs this type; it is an implementation detail, not part of the public API.</p>
     */
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

        /**
         * Folds one issue's fields into this accumulator.
         *
         * @param issue              the leaf issue to attribute
         * @param classifier         status/category classifier (injected, not re-instantiated)
         * @param dueDateAvailable   when {@code true} use {@code dueDate} for overdue check;
         *                           otherwise use activity-recency threshold
         * @param today              today's date (computed once per request, passed in)
         * @param overdueThreshold   the cutoff {@link Instant} for the fallback overdue check
         */
        void accumulate(
                ProjectJiraIssue issue,
                JiraHealthClassifier classifier,
                boolean dueDateAvailable,
                LocalDate today,
                Instant overdueThreshold) {

            assigned++;
            recordIssueType(issue);

            boolean isDone = classifier.isDoneStatus(issue.getStatusCategoryKey());

            if (isDone) {
                completed++;
            } else {
                if (classifier.isInProgressStatus(issue.getStatusCategoryKey())) {
                    inProgress++;
                }

                // Overdue detection — two modes depending on dueDateAvailable flag.
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

            // Last active date — keep the most recent jiraUpdatedAt across all issues.
            Instant updatedAt = issue.getJiraUpdatedAt();
            if (updatedAt != null
                    && (lastActiveDate == null || updatedAt.isAfter(lastActiveDate))) {
                lastActiveDate = updatedAt;
            }
        }

        /**
         * Folds only the Story Points and Last Active date from a Parent Issue, bypassing
         * the direct assignment counting to prevent double-counting subtasks.
         */
        void accumulateStoryPoints(ProjectJiraIssue issue, JiraHealthClassifier classifier) {
            BigDecimal sp = issue.getStoryPoints();
            if (sp != null) {
                storyPointsSeen = true;
                storyPointsAssigned = storyPointsAssigned == null
                        ? sp : storyPointsAssigned.add(sp);
                
                if (classifier.isDoneStatus(issue.getStatusCategoryKey())) {
                    storyPointsCompleted = storyPointsCompleted == null
                            ? sp : storyPointsCompleted.add(sp);
                }
            }
            
            Instant updatedAt = issue.getJiraUpdatedAt();
            if (updatedAt != null
                    && (lastActiveDate == null || updatedAt.isAfter(lastActiveDate))) {
                lastActiveDate = updatedAt;
            }
            
            recordIssueType(issue);
        }

        private void recordIssueType(ProjectJiraIssue issue) {
            String type = issue.getIssueType();
            String key = (type == null || type.isBlank()) ? "Unknown" : type;
            issueTypeCounts.merge(key, 1, Integer::sum);
        }

        /**
         * Converts this mutable accumulator into an immutable {@link JiraWorkloadDto.MemberRow}.
         * Story-point fields are {@code null} (not zero) when {@link #storyPointsSeen} is false,
         * preserving the DTO contract that distinguishes "not configured" from "zero points".
         */
        JiraWorkloadDto.MemberRow toMemberRow() {
            int openIssues = assigned - completed;
            double completionRate = assigned == 0
                    ? 0.0
                    : (double) completed / assigned * 100.0;

            return new JiraWorkloadDto.MemberRow(
                    accountId,
                    displayName,
                    assigned,
                    completed,
                    inProgress,
                    overdue,
                    openIssues,
                    storyPointsSeen ? storyPointsAssigned : null,
                    storyPointsSeen ? storyPointsCompleted : null,
                    completionRate,
                    lastActiveDate,
                    Map.copyOf(issueTypeCounts));
        }
    }

    /**
     * Value object capturing the result of the imbalance detection step.
     *
     * <p>Using a record keeps the return type of {@link #detectImbalance} explicit
     * and avoids a raw {@code Object[]} or a mutable pair class.</p>
     */
    private record ImbalanceResult(boolean detected, String message) {

        static ImbalanceResult none() {
            return new ImbalanceResult(false, null);
        }
    }
}
