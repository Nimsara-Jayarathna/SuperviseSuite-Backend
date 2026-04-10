package com.supervisesuite.backend.projects.dto;

import java.time.Instant;
import java.util.List;

public record JiraHealthDto(
    double completionPercent,
    int openIssues,
    int overdueIssues,
    int highPriorityOpen,
    StatusBreakdown statusBreakdown,
    List<TypeCount> typeDistribution,
    double bugRatio,
    Instant lastSyncedAt
) {

    public record StatusBreakdown(
        int toDo,
        int inProgress,
        int done
    ) {}

    public record TypeCount(
        String type,
        long count
    ) {}
}
