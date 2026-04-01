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
 *   <li>Subtask: keep and attribute using its own assignee.
 *   <li>Story/Task with subtasks present in the same list: skip to avoid double counting.
 *   <li>Story/Task without subtasks: keep and attribute using its own assignee.
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

        Set<String> parentKeysWithSubtasks = new HashSet<>();
        for (JiraIssueData issue : issues) {
            if (issue != null && issue.isSubtask() && hasText(issue.getParentKey())) {
                parentKeysWithSubtasks.add(issue.getParentKey());
            }
        }

        List<JiraIssueData> resolvedIssues = new ArrayList<>();
        for (JiraIssueData issue : issues) {
            if (issue == null) {
                continue;
            }

            if (issue.isSubtask()) {
                resolvedIssues.add(issue);
                continue;
            }

            if (hasText(issue.getIssueKey()) && parentKeysWithSubtasks.contains(issue.getIssueKey())) {
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
