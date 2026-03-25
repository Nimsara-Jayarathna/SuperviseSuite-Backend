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
@Table(name = "github_setup_states")
public class GitHubSetupState {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String stateJtiHash;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String flowType;

    private UUID requestId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
