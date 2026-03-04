package com.supervisesuite.backend.supervisor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class UpdateSupervisorProjectMilestoneRequest {
    @NotBlank(message = "Milestone title is required.")
    private String title;

    private String description;

    @NotNull(message = "Milestone due date is required.")
    private LocalDate dueDate;

    @NotBlank(message = "Milestone status is required.")
    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
