package com.supervisesuite.backend.projects.dto;

import java.time.Instant;

public class ProjectCommitDto {
    private String sha;
    private String message;
    private String author;
    private Instant committedAt;

    public ProjectCommitDto() {
    }

    public ProjectCommitDto(String sha, String message, String author, Instant committedAt) {
        this.sha = sha;
        this.message = message;
        this.author = author;
        this.committedAt = committedAt;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public void setCommittedAt(Instant committedAt) {
        this.committedAt = committedAt;
    }
}