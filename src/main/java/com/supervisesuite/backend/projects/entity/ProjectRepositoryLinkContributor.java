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
@Table(name = "project_repository_link_contributors")
public class ProjectRepositoryLinkContributor {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectRepositoryLinkId;

    @Column(nullable = false)
    private String contributorName;

    @Column(nullable = false)
    private Integer commitCount;

    private Instant lastContributionAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
