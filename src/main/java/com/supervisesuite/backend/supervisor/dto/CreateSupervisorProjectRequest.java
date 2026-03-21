package com.supervisesuite.backend.supervisor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class CreateSupervisorProjectRequest {
    @NotBlank(message = "Title is required.")
    private String title;

    @NotBlank(message = "Summary is required.")
    private String summary;

    @NotBlank(message = "Batch is required.")
    private String batch;

    @NotBlank(message = "Semester is required.")
    private String semester;

    @NotEmpty(message = "At least one student must be selected.")
    private List<@NotNull(message = "Student ID is required.") UUID> studentIds;

    @Valid
    @NotEmpty(message = "At least one milestone is required.")
    private List<@NotNull(message = "Milestone is required.") InitialMilestone> milestones;

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

    public List<UUID> getStudentIds() {
        return studentIds;
    }

    public void setStudentIds(List<UUID> studentIds) {
        this.studentIds = studentIds;
    }

    public List<InitialMilestone> getMilestones() {
        return milestones;
    }

    public void setMilestones(List<InitialMilestone> milestones) {
        this.milestones = milestones;
    }

    public static class InitialMilestone {
        @NotBlank(message = "Milestone title is required.")
        private String title;

        private String description;

        @NotNull(message = "Milestone due date is required.")
        private LocalDate dueDate;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public LocalDate getDueDate() {
            return dueDate;
        }

        public void setDueDate(LocalDate dueDate) {
            this.dueDate = dueDate;
        }
    }
}
