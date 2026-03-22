package com.supervisesuite.backend.projects.dto;

import java.util.UUID;

public class GitHubInstallationRepositoryDto {

    private Long repositoryId;
    private String name;
    private String fullName;
    private String url;
    private String ownerLogin;
    private String defaultBranch;
    private boolean alreadyLinked;
    private UUID linkedProjectId;

    public GitHubInstallationRepositoryDto() {
    }

    public GitHubInstallationRepositoryDto(
        Long repositoryId,
        String name,
        String fullName,
        String url,
        String ownerLogin,
        String defaultBranch,
        boolean alreadyLinked,
        UUID linkedProjectId
    ) {
        this.repositoryId = repositoryId;
        this.name = name;
        this.fullName = fullName;
        this.url = url;
        this.ownerLogin = ownerLogin;
        this.defaultBranch = defaultBranch;
        this.alreadyLinked = alreadyLinked;
        this.linkedProjectId = linkedProjectId;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(Long repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOwnerLogin() {
        return ownerLogin;
    }

    public void setOwnerLogin(String ownerLogin) {
        this.ownerLogin = ownerLogin;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public boolean isAlreadyLinked() {
        return alreadyLinked;
    }

    public void setAlreadyLinked(boolean alreadyLinked) {
        this.alreadyLinked = alreadyLinked;
    }

    public UUID getLinkedProjectId() {
        return linkedProjectId;
    }

    public void setLinkedProjectId(UUID linkedProjectId) {
        this.linkedProjectId = linkedProjectId;
    }
}
