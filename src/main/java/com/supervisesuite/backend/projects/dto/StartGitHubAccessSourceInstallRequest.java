package com.supervisesuite.backend.projects.dto;

public class StartGitHubAccessSourceInstallRequest {

    private String projectId;

    private String requestToken;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRequestToken() {
        return requestToken;
    }

    public void setRequestToken(String requestToken) {
        this.requestToken = requestToken;
    }
}
