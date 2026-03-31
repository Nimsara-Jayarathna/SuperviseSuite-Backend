package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import java.util.List;

/**
 * Contract for fetching all issues from a Jira project via the Jira REST API.
 *
 * <p>Callers receive a flat list of {@link JiraIssueData} objects representing
 * every issue (stories, tasks, subtasks) in the connected workspace.
 * Pagination and token decryption are handled entirely by the implementation.
 */
public interface JiraIssueClient {

    /**
     * Fetches all issues for the Jira project associated with the given integration record.
     *
     * @param integration the active {@link ProjectJiraIntegration} containing the cloudId
     *                    and encrypted access token for the workspace
     * @return a flat list of all issues across all pages; never null, may be empty
     */
    List<JiraIssueData> fetchProjectIssues(ProjectJiraIntegration integration);
}
