package com.supervisesuite.backend.meetings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project_meeting_channels")
public class ProjectMeetingChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "platform", nullable = false, length = 32)
    private String platform;

    @Column(name = "channel_name", nullable = false, length = 120)
    private String channelName;

    @Column(name = "link_or_identifier", nullable = false, length = 1024)
    private String linkOrIdentifier;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    @Column(name = "added_by_name", nullable = false, length = 255)
    private String addedByName;

    @Column(name = "added_by_role", nullable = false, length = 64)
    private String addedByRole;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_by_name", length = 255)
    private String approvedByName;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

