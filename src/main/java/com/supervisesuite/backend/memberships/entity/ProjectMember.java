package com.supervisesuite.backend.memberships.entity;

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
@Table(name = "project_members")
public class ProjectMember {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID projectId;
}
