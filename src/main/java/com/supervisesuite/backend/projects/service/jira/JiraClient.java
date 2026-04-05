package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import java.util.List;

public interface JiraClient {

    List<JiraIssueDto> fetchAllIssues(String cloudId, String accessToken);
}
