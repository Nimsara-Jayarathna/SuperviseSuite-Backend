package com.supervisesuite.backend.projects.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
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
@Table(name = "project_repository_links")
public class ProjectRepositoryLink {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private UUID githubRepositoryId;

    private Long githubInstallationId;

    @Column(nullable = false)
    private Long githubRepoId;

    @Column(nullable = false)
    private String repositoryUrl;

    @Column(nullable = false)
    private String repositoryName;

    private String defaultBranch;

    private String customName;

    @Column(nullable = false)
    private Boolean isPrimary;

    @Column(nullable = false)
    private Boolean isEnabled;

    @Column(nullable = false)
    private Instant linkedAt;

    private Instant lastSyncedAt;

    private Instant lastSyncAttemptedAt;

    private String syncStatus;

    @Column(columnDefinition = "TEXT")
    private String syncError;

    private UUID linkedBySupervisorUserId;

    @Column(nullable = false)
    private String accessType;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;
}
