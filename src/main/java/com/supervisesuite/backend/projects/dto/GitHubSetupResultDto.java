package com.supervisesuite.backend.projects.dto;

public class GitHubSetupResultDto {
    private Long installationId;
    private String accountLogin;
    private String accountType;
    private String setupAction;
    private String linkedProjectId;
    private String redirectUrl;

    public GitHubSetupResultDto() {
    }

    public GitHubSetupResultDto(
        Long installationId,
        String accountLogin,
        String accountType,
        String setupAction,
        String linkedProjectId,
        String redirectUrl
    ) {
        this.installationId = installationId;
        this.accountLogin = accountLogin;
        this.accountType = accountType;
        this.setupAction = setupAction;
        this.linkedProjectId = linkedProjectId;
        this.redirectUrl = redirectUrl;
    }

    public Long getInstallationId() {
        return installationId;
    }

    public void setInstallationId(Long installationId) {
        this.installationId = installationId;
    }

    public String getAccountLogin() {
        return accountLogin;
    }

    public void setAccountLogin(String accountLogin) {
        this.accountLogin = accountLogin;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getSetupAction() {
        return setupAction;
    }

    public void setSetupAction(String setupAction) {
        this.setupAction = setupAction;
    }

    public String getLinkedProjectId() {
        return linkedProjectId;
    }

    public void setLinkedProjectId(String linkedProjectId) {
        this.linkedProjectId = linkedProjectId;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }
}

