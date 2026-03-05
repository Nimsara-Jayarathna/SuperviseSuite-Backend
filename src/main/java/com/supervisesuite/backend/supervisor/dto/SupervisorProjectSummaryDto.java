package com.supervisesuite.backend.supervisor.dto;

import java.time.LocalDate;
import java.util.UUID;

public class SupervisorProjectSummaryDto {
    private UUID id;
    private String title;
    private String summary;
    private String lifecycleStatus;
    private String batch;
    private String semester;
    private LocalDate milestoneDate;
    private Integer progressPercent;
    private String healthNote;
    private long memberCount;

    public SupervisorProjectSummaryDto() {
    }

    public SupervisorProjectSummaryDto(
        UUID id,
        String title,
        String summary,
        String lifecycleStatus,
        String batch,
        String semester,
        LocalDate milestoneDate,
        Integer progressPercent,
        String healthNote,
        long memberCount
    ) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.lifecycleStatus = lifecycleStatus;
        this.batch = batch;
        this.semester = semester;
        this.milestoneDate = milestoneDate;
        this.progressPercent = progressPercent;
        this.healthNote = healthNote;
        this.memberCount = memberCount;
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

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
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

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getHealthNote() {
        return healthNote;
    }

    public void setHealthNote(String healthNote) {
        this.healthNote = healthNote;
    }

    public long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(long memberCount) {
        this.memberCount = memberCount;
    }
}
