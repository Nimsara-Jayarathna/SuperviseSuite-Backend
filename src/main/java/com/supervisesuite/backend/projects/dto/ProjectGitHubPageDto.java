package com.supervisesuite.backend.projects.dto;

import java.util.List;

public class ProjectGitHubPageDto<T> {
    private List<T> items;
    private int page;
    private int size;
    private boolean hasMore;

    public ProjectGitHubPageDto() {
    }

    public ProjectGitHubPageDto(List<T> items, int page, int size, boolean hasMore) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.hasMore = hasMore;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
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

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
