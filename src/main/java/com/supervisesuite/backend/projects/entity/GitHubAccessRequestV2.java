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
@Table(name = "github_access_requests_v2")
public class GitHubAccessRequestV2 {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private UUID requestedByUserId;

    @Column(nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    private String resultTokenHash;

    private Instant resultExpiresAt;

    private Instant resultAcknowledgedAt;

    private Long installationId;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
