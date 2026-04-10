package com.supervisesuite.backend.projects.dto;

import java.time.Instant;
import java.util.List;

public record JiraSprintProgressDto(
        SprintSummary activeSprint,
        List<SprintSummary> recentSprints,
        List<VelocityWeek> velocityWeeks,
        boolean backlogGrowing,
        boolean sprintDataAvailable) {

    public record SprintSummary(
            Long sprintId,
            String sprintName,
            String sprintState,
            Instant startDate,
            Instant endDate,
            Integer sprintStartIssueCount,
            double completionPercent,
            int issuesDone,
            int issuesTotal,
            double sprintPointsDone,
            double sprintPointsTotal,
            boolean sprintPointsAvailable) {
    }

    public record VelocityWeek(
            Instant weekStart,
            long created,
            long resolved,
            Double averageCycleDays) {
    }
}
