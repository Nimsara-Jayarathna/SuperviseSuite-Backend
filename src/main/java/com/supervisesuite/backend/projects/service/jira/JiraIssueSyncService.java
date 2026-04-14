package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.service.SyncAttemptSource;
import java.util.UUID;

public interface JiraIssueSyncService {

    void syncProjectIssues(UUID projectId);

    void syncProjectIssues(UUID projectId, SyncAttemptSource source);
}
