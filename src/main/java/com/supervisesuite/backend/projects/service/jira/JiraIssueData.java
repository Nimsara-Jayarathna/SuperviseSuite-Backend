package com.supervisesuite.backend.projects.service.jira;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Plain Java class representing a single Jira issue after it has been
 * fetched and parsed from the Jira REST API response.
 *
 * <p>This is an internal data class used only within the service layer.
 * It is never serialized directly to the client.
 */
@Getter
@NoArgsConstructor
public class JiraIssueData {

    /** Jira issue key, e.g. "SCRUM-42". */
    private String issueKey;

    /** The issue title from Jira's summary field. */
    private String summary;

    /** Issue type name, e.g. "Story", "Task", "Subtask", "Sub-task". */
    private String issueType;

    /** True if {@link #issueType} indicates a subtask. */
    private boolean subtask;

    /** Key of the parent issue when this is a subtask; null otherwise. */
    private String parentKey;

    /** Status name, e.g. "In Progress". */
    private String statusName;

    /**
     * High-level status category, e.g. "To Do", "In Progress", "Done".
     * Derived from the Jira status category field.
     */
    private String statusCategory;

    /**
     * Stable status category key from Jira ("new", "indeterminate", "done").
     */
    private String statusCategoryKey;

    /** Jira account ID of the assignee; null when unassigned. */
    private String assigneeAccountId;

    /** Display name of the assignee; null when unassigned. */
    private String assigneeDisplayName;

    /**
     * Story points from {@code customfield_10016}.
     * Null when the field is not set on the issue.
     */
    private Double storyPoints;

    /** Due date of the issue; null when not set in Jira. */
    private LocalDate dueDate;

    /** Timestamp of the last update on this issue (the Jira "updated" field). */
    private Instant lastUpdated;

    public JiraIssueData(
            String issueKey,
            String summary,
            String issueType,
            boolean subtask,
            String parentKey,
            String statusName,
            String statusCategory,
            String statusCategoryKey,
            String assigneeAccountId,
            String assigneeDisplayName,
            Double storyPoints,
            LocalDate dueDate,
            Instant lastUpdated) {
        this.issueKey = issueKey;
        this.summary = summary;
        this.issueType = issueType;
        this.subtask = subtask;
        this.parentKey = parentKey;
        this.statusName = statusName;
        this.statusCategory = statusCategory;
        this.statusCategoryKey = statusCategoryKey;
        this.assigneeAccountId = assigneeAccountId;
        this.assigneeDisplayName = assigneeDisplayName;
        this.storyPoints = storyPoints;
        this.dueDate = dueDate;
        this.lastUpdated = lastUpdated;
    }
}
