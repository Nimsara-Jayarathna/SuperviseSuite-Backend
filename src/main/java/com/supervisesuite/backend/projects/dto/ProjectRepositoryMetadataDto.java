package com.supervisesuite.backend.projects.dto;

public class ProjectRepositoryMetadataDto {
    private Long externalRepositoryId;
    private String ownerLogin;
    private String name;
    private String url;
    private String defaultBranch;

    public ProjectRepositoryMetadataDto() {
    }

    public ProjectRepositoryMetadataDto(
        Long externalRepositoryId,
        String ownerLogin,
        String name,
        String url,
        String defaultBranch
    ) {
        this.externalRepositoryId = externalRepositoryId;
        this.ownerLogin = ownerLogin;
        this.name = name;
        this.url = url;
        this.defaultBranch = defaultBranch;
    }

    public Long getExternalRepositoryId() {
        return externalRepositoryId;
    }

    public void setExternalRepositoryId(Long externalRepositoryId) {
        this.externalRepositoryId = externalRepositoryId;
    }

    public String getOwnerLogin() {
        return ownerLogin;
    }

    public void setOwnerLogin(String ownerLogin) {
        this.ownerLogin = ownerLogin;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }
}
