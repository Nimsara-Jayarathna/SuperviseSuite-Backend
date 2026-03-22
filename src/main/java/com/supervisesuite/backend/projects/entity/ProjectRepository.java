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
@Table(name = "project_repositories")
public class ProjectRepository {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String provider;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String repositoryUrl;

    private Long installationId;

    private Long repositoryExternalId;

    private String ownerLogin;

    private String repositoryName;

    private String defaultBranch;

    private UUID linkedBySupervisorUserId;

    private Instant linkedAt;

    @Column(nullable = false)
    private Boolean isPrimary;

    private Instant lastSyncedAt;

    private String syncStatus;

    @Column(columnDefinition = "TEXT")
    private String lastSyncError;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
