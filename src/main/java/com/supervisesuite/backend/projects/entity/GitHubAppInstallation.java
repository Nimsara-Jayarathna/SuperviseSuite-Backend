package com.supervisesuite.backend.projects.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "github_app_installations")
public class GitHubAppInstallation {

    @Id
    private Long installationId;

    private Long accountId;

    private String accountLogin;

    private String accountType;

    @Column(nullable = false)
    private String status;

    private Instant installedAt;

    private Instant lastEventAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}

