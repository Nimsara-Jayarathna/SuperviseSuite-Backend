package com.supervisesuite.backend.projects.dto;

/**
 * Workload summary for a single team member, returned as part of
 * {@link TeamWorkloadResponseDto}.
 */
public class TeamWorkloadStudentDto {

    /** Full display name of the student as returned by Jira. */
    private String displayName;

    /** Jira account ID that uniquely identifies this student. */
    private String accountId;

    /** Total number of issues assigned to this student. */
    private int assigned;

    /** Number of issues in a "Done" status category. */
    private int completed;

    /** Number of issues in an "In Progress" status category. */
    private int inProgress;

    /**
     * Number of assigned issues whose due date is in the past
     * and have not yet reached a "Done" status category.
     */
    private int overdue;

    /** Sum of story points across all assigned issues. */
    private double storyPointsAssigned;

    /** Sum of story points for issues in a "Done" status category. */
    private double storyPointsCompleted;

    /**
     * ISO date string ("yyyy-MM-dd") of the most recent issue update
     * for this student. Null when no issues are assigned.
     */
    private String lastActiveDate;

    /**
     * Completion rate as a whole-number percentage (0–100).
     * Calculated as (completed / assigned) * 100.
     * Zero when {@link #assigned} is 0 to avoid division by zero.
     */
    private int completionRate;

    public TeamWorkloadStudentDto() {
    }

    public TeamWorkloadStudentDto(
            String displayName,
            String accountId,
            int assigned,
            int completed,
            int inProgress,
            int overdue,
            double storyPointsAssigned,
            double storyPointsCompleted,
            String lastActiveDate,
            int completionRate) {
        this.displayName = displayName;
        this.accountId = accountId;
        this.assigned = assigned;
        this.completed = completed;
        this.inProgress = inProgress;
        this.overdue = overdue;
        this.storyPointsAssigned = storyPointsAssigned;
        this.storyPointsCompleted = storyPointsCompleted;
        this.lastActiveDate = lastActiveDate;
        this.completionRate = completionRate;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public int getOpenIssues() {
        return Math.max(0, assigned - completed);
    }

    public int getAssigned() {
        return assigned;
    }

    public void setAssigned(int assigned) {
        this.assigned = assigned;
    }

    public int getCompleted() {
        return completed;
    }

    public void setCompleted(int completed) {
        this.completed = completed;
    }

    public int getInProgress() {
        return inProgress;
    }

    public void setInProgress(int inProgress) {
        this.inProgress = inProgress;
    }

    public int getOverdue() {
        return overdue;
    }

    public void setOverdue(int overdue) {
        this.overdue = overdue;
    }

    public double getStoryPointsAssigned() {
        return storyPointsAssigned;
    }

    public void setStoryPointsAssigned(double storyPointsAssigned) {
        this.storyPointsAssigned = storyPointsAssigned;
    }

    public double getStoryPointsCompleted() {
        return storyPointsCompleted;
    }

    public void setStoryPointsCompleted(double storyPointsCompleted) {
        this.storyPointsCompleted = storyPointsCompleted;
    }

    public String getLastActiveDate() {
        return lastActiveDate;
    }

    public void setLastActiveDate(String lastActiveDate) {
        this.lastActiveDate = lastActiveDate;
    }

    public int getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(int completionRate) {
        this.completionRate = completionRate;
    }
}
