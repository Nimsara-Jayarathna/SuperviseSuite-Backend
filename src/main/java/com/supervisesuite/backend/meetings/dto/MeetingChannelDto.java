package com.supervisesuite.backend.meetings.dto;

import java.time.Instant;
import java.util.UUID;

public record MeetingChannelDto(
    UUID id,
    UUID projectId,
    String platform,
    String channelName,
    String linkOrIdentifier,
    UUID addedBy,
    String addedByName,
    String addedByRole,
    String status,
    UUID approvedBy,
    String approvedByName,
    Instant approvedAt,
    Instant createdAt,
    Instant updatedAt
) {}

