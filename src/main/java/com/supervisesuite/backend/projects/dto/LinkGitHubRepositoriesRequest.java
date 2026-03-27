package com.supervisesuite.backend.projects.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class LinkGitHubRepositoriesRequest {

    @NotBlank
    private String projectId;

    @NotBlank
    private String sourceId;

    @NotEmpty
    @Valid
    private List<Selection> repositories;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public List<Selection> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<Selection> repositories) {
        this.repositories = repositories;
    }

    public static class Selection {

        @NotBlank
        private String githubRepositoryId;

        private String customName;

        private Boolean primary;

        public String getGithubRepositoryId() {
            return githubRepositoryId;
        }

        public void setGithubRepositoryId(String githubRepositoryId) {
            this.githubRepositoryId = githubRepositoryId;
        }

        public String getCustomName() {
            return customName;
        }

        public void setCustomName(String customName) {
            this.customName = customName;
        }

        public Boolean getPrimary() {
            return primary;
        }

        public void setPrimary(Boolean primary) {
            this.primary = primary;
        }
    }
}
