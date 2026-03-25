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
@Table(name = "github_access_sources")
public class GitHubAccessSource {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    private Long installationId;

    @Column(nullable = false)
    private String ownerLogin;

    @Column(nullable = false)
    private String ownerType;

    @Column(nullable = false)
    private String accessType;

    @Column(nullable = false)
    private UUID createdByUserId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Boolean isActive;

    private Instant updatedAt;
}
