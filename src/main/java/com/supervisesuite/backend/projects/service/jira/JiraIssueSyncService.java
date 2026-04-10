package com.supervisesuite.backend.projects.service.jira;

import java.util.UUID;

public interface JiraIssueSyncService {

    void syncProjectIssues(UUID projectId);
}
