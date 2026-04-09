package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import java.util.UUID;

/**
 * Computes team workload distribution from the synced Jira issue cache.
 *
 * <p>Implementations must read exclusively from {@code project_jira_issues} —
 * no live Jira API calls are made at query time.</p>
 */
public interface JiraWorkloadService {

    /**
     * Returns a {@link JiraWorkloadDto} containing per-member workload metrics,
     * imbalance detection state, and unassigned issue count for the given project.
     *
     * @param projectId the project whose synced Jira issues are used for computation
     * @return workload snapshot; never {@code null}
     */
    JiraWorkloadDto getWorkload(UUID projectId);
}
