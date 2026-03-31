package com.supervisesuite.backend.projects.dto;

import java.util.List;

/**
 * Top-level response object for the Team Workload Analytics endpoint.
 *
 * <p>Contains the per-student breakdown plus aggregate metadata about
 * unassigned issues and workload imbalance detection.
 */
public class TeamWorkloadResponseDto {

    /** Workload summary for each team member who has at least one issue. */
    private List<TeamWorkloadStudentDto> students;

    /** Number of issues in the project that have no assignee. */
    private int unassignedIssues;

    /**
     * True when a significant workload imbalance is detected among team members.
     * The detection heuristic is determined by the service layer.
     */
    private boolean imbalanceDetected;

    /**
     * Human-readable description of the detected imbalance.
     * Null when {@link #imbalanceDetected} is false.
     */
    private String imbalanceMessage;

    /**
     * True when at least one issue in the project has a due date set.
     * Used by the frontend to decide whether overdue counts are real or zero.
     */
    private boolean dueDateAvailable;

    public TeamWorkloadResponseDto() {
    }

    public TeamWorkloadResponseDto(
            List<TeamWorkloadStudentDto> students,
            int unassignedIssues,
            boolean imbalanceDetected,
            String imbalanceMessage,
            boolean dueDateAvailable) {
        this.students = students;
        this.unassignedIssues = unassignedIssues;
        this.imbalanceDetected = imbalanceDetected;
        this.imbalanceMessage = imbalanceMessage;
        this.dueDateAvailable = dueDateAvailable;
    }

    public List<TeamWorkloadStudentDto> getStudents() {
        return students;
    }

    public void setStudents(List<TeamWorkloadStudentDto> students) {
        this.students = students;
    }

    public int getUnassignedIssues() {
        return unassignedIssues;
    }

    public void setUnassignedIssues(int unassignedIssues) {
        this.unassignedIssues = unassignedIssues;
    }

    public boolean isImbalanceDetected() {
        return imbalanceDetected;
    }

    public void setImbalanceDetected(boolean imbalanceDetected) {
        this.imbalanceDetected = imbalanceDetected;
    }

    public String getImbalanceMessage() {
        return imbalanceMessage;
    }

    public void setImbalanceMessage(String imbalanceMessage) {
        this.imbalanceMessage = imbalanceMessage;
    }

    public boolean isDueDateAvailable() {
        return dueDateAvailable;
    }

    public void setDueDateAvailable(boolean dueDateAvailable) {
        this.dueDateAvailable = dueDateAvailable;
    }
}
