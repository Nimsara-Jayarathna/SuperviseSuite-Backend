package com.supervisesuite.backend.meetings.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MeetingRecordDto(
    UUID id,
    UUID projectId,
    LocalDate meetingDate,
    int durationMinutes,
    String discussionSummary,
    String discussionDetails,
    UUID channelId,
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

