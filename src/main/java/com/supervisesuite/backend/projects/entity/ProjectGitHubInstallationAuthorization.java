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
@Table(name = "project_github_installation_authorizations")
public class ProjectGitHubInstallationAuthorization {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private Long installationId;

    @Column(nullable = false)
    private UUID authorizedBySupervisorUserId;

    @Column(nullable = false)
    private Instant authorizedAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
