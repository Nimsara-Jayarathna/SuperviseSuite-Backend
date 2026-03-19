package com.supervisesuite.backend.projects.dto;

import java.util.List;

public class ProjectCommitActivityDto {
    private boolean repositoryLinked;
    private List<ProjectCommitDto> commits;

    public ProjectCommitActivityDto() {
    }

    public ProjectCommitActivityDto(boolean repositoryLinked, List<ProjectCommitDto> commits) {
        this.repositoryLinked = repositoryLinked;
        this.commits = commits;
    }

    public boolean isRepositoryLinked() {
        return repositoryLinked;
    }

    public void setRepositoryLinked(boolean repositoryLinked) {
        this.repositoryLinked = repositoryLinked;
    }

    public List<ProjectCommitDto> getCommits() {
        return commits;
    }

    public void setCommits(List<ProjectCommitDto> commits) {
        this.commits = commits;
    }
}