package com.supervisesuite.backend.projects.dto;

public record JiraIssueSummaryDto(
    String issueKey,
    String summary,
    String issueType,
    String statusName,
    String statusCategoryKey,
    String assigneeDisplayName,
    String parentKey,
    Long sprintId,
    String sprintName
) {}
