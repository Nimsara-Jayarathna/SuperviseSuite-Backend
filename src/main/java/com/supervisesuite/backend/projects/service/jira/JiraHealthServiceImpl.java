package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrator service adhering to SRP that delegates health calculation rules to a domain component.
 */
@Service
class JiraHealthServiceImpl implements JiraHealthService {

    private final ProjectJiraIssueRepository jiraIssueRepository;
    private final JiraHealthMetricsAggregator aggregator;

    JiraHealthServiceImpl(
            ProjectJiraIssueRepository jiraIssueRepository,
            JiraHealthMetricsAggregator aggregator) {
        this.jiraIssueRepository = jiraIssueRepository;
        this.aggregator = aggregator;
    }

    @Override
    @Transactional(readOnly = true)
    public JiraHealthDto getHealthOverview(UUID projectId) {
        List<ProjectJiraIssue> issues = jiraIssueRepository.findAllByProjectId(projectId);

        if (issues.isEmpty()) {
            return new JiraHealthDto(0.0, 0, 0, 0, 
                new JiraHealthDto.StatusBreakdown(0, 0, 0), 
                List.of(), 0.0, null);
        }

        LocalDate today = LocalDate.now();
        JiraHealthMetricsAggregator.Result result = aggregator.aggregate(issues, today);

        Instant lastSyncedAt = jiraIssueRepository
                .findMaxSyncedAtByProjectId(projectId)
                .orElse(null);

        return new JiraHealthDto(
                result.completionPercent(),
                result.openIssues(),
                result.overdueCount(),
                result.highPriorityOpen(),
                result.statusBreakdown(),
                result.typeDistribution(),
                result.bugRatio(),
                lastSyncedAt);
    }
}
