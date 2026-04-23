package com.supervisesuite.backend.supervisor.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public class UpdateSupervisorProjectRequest {
    @NotBlank(message = "Title is required.")
    private String title;

    @NotBlank(message = "Summary is required.")
    private String summary;

    @NotBlank(message = "Batch is required.")
    private String batch;

    @NotBlank(message = "Semester is required.")
    private String semester;

    @NotBlank(message = "Lifecycle status is required.")
    private String lifecycleStatus;

    private UUID leaderStudentId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public UUID getLeaderStudentId() {
        return leaderStudentId;
    }

    public void setLeaderStudentId(UUID leaderStudentId) {
        this.leaderStudentId = leaderStudentId;
    }
}
