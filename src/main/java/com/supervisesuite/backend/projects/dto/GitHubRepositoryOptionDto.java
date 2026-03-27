package com.supervisesuite.backend.projects.dto;

public class GitHubRepositoryOptionDto {

    private String id;
    private Long githubRepoId;
    private String fullName;
    private String name;
    private String ownerLogin;
    private String defaultBranch;
    private String url;

    public GitHubRepositoryOptionDto() {
    }

    public GitHubRepositoryOptionDto(
        String id,
        Long githubRepoId,
        String fullName,
        String name,
        String ownerLogin,
        String defaultBranch,
        String url
    ) {
        this.id = id;
        this.githubRepoId = githubRepoId;
        this.fullName = fullName;
        this.name = name;
        this.ownerLogin = ownerLogin;
        this.defaultBranch = defaultBranch;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getGithubRepoId() {
        return githubRepoId;
    }

    public void setGithubRepoId(Long githubRepoId) {
        this.githubRepoId = githubRepoId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
