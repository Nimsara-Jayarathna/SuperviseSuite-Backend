package com.supervisesuite.backend.projects.dto;

import java.util.List;

public class GitHubInstallationRepositoryPageDto {

    private List<GitHubInstallationRepositoryDto> items;
    private int page;
    private int size;
    private int returnedCount;
    private Long totalCount;
    private boolean hasNext;
    private boolean hasPrevious;
    private Integer nextPage;

    public GitHubInstallationRepositoryPageDto() {
    }

    public GitHubInstallationRepositoryPageDto(
        List<GitHubInstallationRepositoryDto> items,
        int page,
        int size,
        int returnedCount,
        Long totalCount,
        boolean hasNext,
        boolean hasPrevious,
        Integer nextPage
    ) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.returnedCount = returnedCount;
        this.totalCount = totalCount;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
        this.nextPage = nextPage;
    }

    public List<GitHubInstallationRepositoryDto> getItems() {
        return items;
    }

    public void setItems(List<GitHubInstallationRepositoryDto> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getReturnedCount() {
        return returnedCount;
    }

    public void setReturnedCount(int returnedCount) {
        this.returnedCount = returnedCount;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    public boolean isHasPrevious() {
        return hasPrevious;
    }

    public void setHasPrevious(boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }

    public Integer getNextPage() {
        return nextPage;
    }

    public void setNextPage(Integer nextPage) {
        this.nextPage = nextPage;
    }
}
