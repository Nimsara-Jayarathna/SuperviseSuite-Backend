package com.supervisesuite.backend.projects.dto;

public class ProjectRepositoryMetadataDto {
    private String name;
    private String url;
    private String defaultBranch;

    public ProjectRepositoryMetadataDto() {
    }

    public ProjectRepositoryMetadataDto(String name, String url, String defaultBranch) {
        this.name = name;
        this.url = url;
        this.defaultBranch = defaultBranch;
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
