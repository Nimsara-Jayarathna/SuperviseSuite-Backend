package com.supervisesuite.backend.supervisor.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateSupervisorProjectStatusRequest {
    @NotBlank(message = "Lifecycle status is required.")
    private String lifecycleStatus;

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }
}
