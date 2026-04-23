package com.supervisesuite.backend.projects.entity;

import com.supervisesuite.backend.users.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Column(name = "title", nullable = false)
    private String name;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String description;

    @Column(name = "lifecycle_status", nullable = false)
    private String status;

    /** Owning supervisor. Nullable for existing rows that predate V2 migration. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private User supervisor;

    /** Soft delete marker. Null means the project is active. */
    private Instant deletedAt;

    private String batch;

    private String semester;

    private Integer progressPercent;

    private LocalDate milestoneDate;

    private Instant lastActivityAt;

    @Column(columnDefinition = "TEXT")
    private String communicationUrl;

    private UUID leaderUserId;

}
