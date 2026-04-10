package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Domain processor responsible for sanitizing and correlating data immediately 
 * prior to and after database mapping during a sync operation.
 */
@Component
class JiraIssueSyncProcessor {

    /**
     * Jira can occasionally return duplicate issue keys across pages.
     * Deduplicate to avoid unique-constraint conflicts on (project_id, issue_key).
     */
    public List<JiraIssueDto> deduplicate(List<JiraIssueDto> fetchedIssues) {
        Map<String, JiraIssueDto> uniqueDtosByKey = new LinkedHashMap<>();
        for (JiraIssueDto dto : fetchedIssues) {
            String issueKey = dto.getKey();
            if (issueKey != null && !issueKey.isBlank()) {
                uniqueDtosByKey.putIfAbsent(issueKey, dto);
            }
        }
        return new ArrayList<>(uniqueDtosByKey.values());
    }

    /**
     * Backfill parentIssueType — O(n) pass.
     * For each entity that declares a parentKey, look up the parent entity in the same
     * batch and write the parent's issueType onto the parent entity as parentIssueType.
     * This marks an issue as being a parent of subtasks so analytics engines can filter efficiently.
     */
    public void processRelationships(List<ProjectJiraIssue> issues) {
        Map<String, ProjectJiraIssue> byKey = new HashMap<>();
        for (ProjectJiraIssue issue : issues) {
            byKey.put(issue.getIssueKey(), issue);
        }
        for (ProjectJiraIssue issue : issues) {
            String parentKey = issue.getParentKey();
            if (parentKey != null) {
                ProjectJiraIssue parentEntity = byKey.get(parentKey);
                if (parentEntity != null && parentEntity.getIssueType() != null) {
                    parentEntity.setParentIssueType(parentEntity.getIssueType());
                }
            }
        }
    }
}
