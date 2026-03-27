package com.supervisesuite.backend.projects.dto;

public class GitHubWebhookResultDto {
    private String event;
    private String action;
    private Long installationId;
    private String status;

    public GitHubWebhookResultDto() {
    }

    public GitHubWebhookResultDto(String event, String action, Long installationId, String status) {
        this.event = event;
        this.action = action;
        this.installationId = installationId;
        this.status = status;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getInstallationId() {
        return installationId;
    }

    public void setInstallationId(Long installationId) {
        this.installationId = installationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

