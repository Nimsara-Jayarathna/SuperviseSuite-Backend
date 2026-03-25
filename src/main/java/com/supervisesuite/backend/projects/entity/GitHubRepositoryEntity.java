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
@Table(name = "github_repositories")
public class GitHubRepositoryEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID accessSourceId;

    @Column(nullable = false)
    private Long githubRepoId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String name;

    private String defaultBranch;

    @Column(columnDefinition = "TEXT")
    private String htmlUrl;

    private String ownerLogin;

    @Column(nullable = false)
    private Instant createdAt;
}
