package com.supervisesuite.backend.projects.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project_jira_issues")
public class ProjectJiraIssue {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 32)
    private String issueKey;

    @Column(length = 512)
    private String summary;

    @Column(length = 64)
    private String issueType;

    @Column(length = 128)
    private String statusName;

    @Column(length = 32)
    private String statusCategoryKey;

    @Column(length = 128)
    private String assigneeAccountId;

    @Column(length = 255)
    private String assigneeDisplayName;

    @Column(length = 64)
    private String priorityName;

    @Column(precision = 6, scale = 1)
    private BigDecimal storyPoints;

    private LocalDate dueDate;

    private Instant resolutionDate;

    @Column(length = 32)
    private String parentKey;

    /**
     * The issue type of this issue's parent (e.g. "Story", "Epic").
     * Populated during sync for issues that have a non-null {@link #parentKey}.
     * Used by the workload service to identify parent issues that should be
     * excluded from direct attribution when their subtasks carry their own assignees.
     * Null for top-level issues and for rows synced before V21 migration.
     */
    @Column(length = 64)
    private String parentIssueType;

    private Long sprintId;

    @Column(length = 255)
    private String sprintName;

    @Column(length = 32)
    private String sprintState;

    private Instant sprintStartDate;

    private Instant sprintEndDate;

    private Instant jiraCreatedAt;

    private Instant jiraUpdatedAt;

    @Column(nullable = false)
    private Instant syncedAt;
}
