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
@Table(name = "project_repository_links")
public class ProjectRepositoryLink {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private UUID githubRepositoryId;

    @Column(nullable = false)
    private Long githubRepoId;

    private String customName;

    @Column(nullable = false)
    private Boolean isPrimary;

    @Column(nullable = false)
    private Instant linkedAt;

    private Instant lastSyncedAt;

    private String syncStatus;

    @Column(columnDefinition = "TEXT")
    private String syncError;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
