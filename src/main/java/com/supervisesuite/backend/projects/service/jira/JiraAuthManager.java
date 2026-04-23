package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;

public interface JiraAuthManager {
    /**
     * Gets a valid Jira access token, or proactively refreshes it if it is expired
     * (or expiring soon).
     *
     * @param integration the project Jira integration details.
     * @return the decrypted, valid Jira access token.
     */
    String getOrRefreshAccessToken(ProjectJiraIntegration integration);
}
