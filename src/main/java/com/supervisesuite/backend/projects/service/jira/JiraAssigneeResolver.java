package com.supervisesuite.backend.projects.service.jira;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Resolves which Jira issues should count toward per-assignee workload.
 *
 * <p>Resolution rules:
 * <ul>
 *   <li>Keep every issue as a countable unit of work for assignee-based metrics.
 *   <li>Drop duplicate issue keys when the same issue appears more than once.
 * </ul>
 */
@Service
public class JiraAssigneeResolver {

    /**
     * Returns the list of issues that represent counted units of work after assignee resolution.
     *
     * @param issues raw Jira issues fetched from the Jira API
     * @return filtered issue list following subtask/parent ownership rules
     */
    public List<JiraIssueData> resolveAssigneeWorkUnits(List<JiraIssueData> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }

        Set<String> seenIssueKeys = new HashSet<>();
        List<JiraIssueData> resolvedIssues = new ArrayList<>();
        for (JiraIssueData issue : issues) {
            if (issue == null) {
                continue;
            }

            String issueKey = issue.getIssueKey();
            if (hasText(issueKey) && !seenIssueKeys.add(issueKey)) {
                continue;
            }

            resolvedIssues.add(issue);
        }

        return resolvedIssues;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
