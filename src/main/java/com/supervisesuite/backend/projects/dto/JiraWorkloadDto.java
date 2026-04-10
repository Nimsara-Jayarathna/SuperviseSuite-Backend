package com.supervisesuite.backend.projects.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of team workload distribution derived from synced Jira issues.
 *
 * <p>This DTO is computed in-memory from {@code project_jira_issues} — no live Jira
 * API call is made at query time. The shape mirrors the four visual zones of the
 * Team Workload panel:</p>
 * <ol>
 *   <li>{@link #imbalanceDetected} / {@link #imbalanceMessage} → alert banner</li>
 *   <li>{@link #members} (open-issues field) → bar chart</li>
 *   <li>{@link #members} (full metrics) → comparison table</li>
 *   <li>{@link #unassignedCount} → unassigned warning card</li>
 * </ol>
 *
 * <p>Subtask attribution: work is credited to subtask assignees; parent stories that
 * have subtasks are excluded from direct attribution to avoid double-counting.</p>
 *
 * @param members           Per-member rows sorted by open issues descending.
 *                          Empty when no assignee data exists (unassigned-only state).
 * @param unassignedCount   Number of issues with no assignee. Independent of
 *                          {@code members} — can be non-zero even when members is empty.
 * @param dueDateAvailable  {@code true} when at least one issue in the project has a
 *                          due date set in Jira. When {@code false}, overdue values are
 *                          derived from activity recency as a fallback and the frontend
 *                          renders an "estimate" badge on the Overdue column header.
 * @param imbalanceDetected {@code true} when max open issues &gt; 3× min open issues
 *                          AND the member with the most open issues has at least 3.
 * @param imbalanceMessage  Human-readable description of the imbalance, e.g.
 *                          "Alice has 3x more open issues than Bob". {@code null}
 *                          when {@code imbalanceDetected} is {@code false}.
 */
public record JiraWorkloadDto(
        List<MemberRow> members,
        int unassignedCount,
        boolean dueDateAvailable,
        boolean imbalanceDetected,
        String imbalanceMessage) {

    /**
     * Per-member workload metrics for one team member's row in the comparison table.
     *
     * <p>Story-points fields use {@link BigDecimal} to preserve the single-decimal
     * precision stored in Jira ({@code customfield_10016}) and are {@code null} — not
     * zero — when story points are not configured in the connected Jira project. The
     * frontend renders {@code —} for null values rather than {@code 0}.</p>
     *
     * @param accountId            Jira account ID — stable unique identifier for the member.
     * @param displayName          Human-readable name shown in the UI (e.g. "Ashen Priyashan").
     * @param assigned             Total issues attributed to this member (denominator of
     *                             {@code completionRate}).
     * @param completed            Issues in a "done" status category.
     * @param inProgress           Issues in an "in-progress" status category.
     * @param overdue              Open issues past their {@code dueDate} (or past the
     *                             7-day activity-recency threshold when no due dates exist).
     * @param openIssues           Derived convenience field: {@code assigned - completed}.
     *                             Drives bar-chart width and sort order.
     * @param storyPointsAssigned  Total story points on all attributed issues. {@code null}
     *                             when story points are not configured.
     * @param storyPointsCompleted Story points on completed issues. {@code null} when
     *                             story points are not configured.
     * @param completionRate       Percentage of assigned issues that are completed (0–100).
     *                             {@code 0.0} when {@code assigned} is zero.
     * @param lastActiveDate       Most recent {@code jiraUpdatedAt} across attributed issues.
     *                             Used to compute "last active N days/hours ago" in the UI.
     * @param issueTypeCounts      A count breakdown by Jira issue type (e.g. Story: 1, Sub-task: 20).
     */
    public record MemberRow(
            String accountId,
            String displayName,
            int assigned,
            int completed,
            int inProgress,
            int overdue,
            int openIssues,
            BigDecimal storyPointsAssigned,
            BigDecimal storyPointsCompleted,
            double completionRate,
            Instant lastActiveDate,
            Map<String, Integer> issueTypeCounts) {
    }
}
