package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import java.time.Instant;
import java.util.UUID;

public interface JiraIssueMapper {

    void mapToEntity(ProjectJiraIssue target, JiraIssueDto source, UUID projectId, Instant syncedAt);
}
