package com.supervisesuite.backend.projects.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project_jira_integrations")
public class ProjectJiraIntegration {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String cloudId;

    @Column(nullable = false)
    private String workspaceName;

    @Column(columnDefinition = "TEXT")
    private String workspaceUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    @Column(columnDefinition = "TEXT")
    private String scope;

    private UUID connectedBy;

    @Column(nullable = false)
    private Instant connectedAt;

    private Instant updatedAt;

    private Instant revokedAt;

    @Transient
    private String jiraProjectKey;
}

