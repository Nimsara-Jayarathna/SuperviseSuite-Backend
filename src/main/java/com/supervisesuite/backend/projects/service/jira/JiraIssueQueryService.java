package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraIssueSummaryDto;
import java.util.List;
import java.util.UUID;

public interface JiraIssueQueryService {
    List<JiraIssueSummaryDto> getIssuesForProject(UUID projectId);
}
