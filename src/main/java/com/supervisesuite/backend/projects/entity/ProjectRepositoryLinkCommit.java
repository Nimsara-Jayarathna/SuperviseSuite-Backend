package com.supervisesuite.backend.projects.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project_repository_link_commits")
public class ProjectRepositoryLinkCommit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectRepositoryLinkId;

    @Column(nullable = false)
    private String sha;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    private String author;

    private String githubUsername;

    private String githubAvatarUrl;

    private Instant committedAt;

    private String commitType;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
