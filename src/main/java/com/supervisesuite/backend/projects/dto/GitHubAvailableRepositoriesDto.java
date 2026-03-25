package com.supervisesuite.backend.projects.dto;

import java.util.List;

public class GitHubAvailableRepositoriesDto {

    private String sourceId;
    private List<GitHubRepositoryOptionDto> items;
    private Integer totalCount;

    public GitHubAvailableRepositoriesDto() {
    }

    public GitHubAvailableRepositoriesDto(String sourceId, List<GitHubRepositoryOptionDto> items, Integer totalCount) {
        this.sourceId = sourceId;
        this.items = items;
        this.totalCount = totalCount;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public List<GitHubRepositoryOptionDto> getItems() {
        return items;
    }

    public void setItems(List<GitHubRepositoryOptionDto> items) {
        this.items = items;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }
}
