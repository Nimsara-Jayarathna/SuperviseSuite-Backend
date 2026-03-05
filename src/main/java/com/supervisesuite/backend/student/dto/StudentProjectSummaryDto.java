package com.supervisesuite.backend.student.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class StudentProjectSummaryDto {
    private UUID id;
    private String title;
    private String summary;
    private String status;
    private String batch;
    private String semester;
    private LocalDate milestoneDate;
    private Instant lastActivityAt;
    private Integer progressPercent;
    private String supervisorName;

    public StudentProjectSummaryDto() {
    }

    public StudentProjectSummaryDto(
        UUID id,
        String title,
        String summary,
        String status,
        String batch,
        String semester,
        LocalDate milestoneDate,
        Instant lastActivityAt,
        Integer progressPercent,
        String supervisorName
    ) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.status = status;
        this.batch = batch;
        this.semester = semester;
        this.milestoneDate = milestoneDate;
        this.lastActivityAt = lastActivityAt;
        this.progressPercent = progressPercent;
        this.supervisorName = supervisorName;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDate getMilestoneDate() {
        return milestoneDate;
    }

    public void setMilestoneDate(LocalDate milestoneDate) {
        this.milestoneDate = milestoneDate;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getSupervisorName() {
        return supervisorName;
    }

    public void setSupervisorName(String supervisorName) {
        this.supervisorName = supervisorName;
    }
}
