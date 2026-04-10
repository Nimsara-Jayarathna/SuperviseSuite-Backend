package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestration service for Team Workload reporting.
 * Delegates statistical aggregation to {@link JiraWorkloadCalculator} and 
 * imbalance heuristic logic to {@link WorkloadImbalanceDetector} strictly following SRP/OCP.
 */
@Service
class JiraWorkloadServiceImpl implements JiraWorkloadService {

    private static final long OVERDUE_FALLBACK_DAYS = 7;

    private final ProjectJiraIssueRepository jiraIssueRepository;
    private final JiraWorkloadCalculator accumulatorCalculator;
    private final WorkloadImbalanceDetector imbalanceDetector;

    JiraWorkloadServiceImpl(
            ProjectJiraIssueRepository jiraIssueRepository,
            JiraWorkloadCalculator accumulatorCalculator,
            WorkloadImbalanceDetector imbalanceDetector) {
        this.jiraIssueRepository = jiraIssueRepository;
        this.accumulatorCalculator = accumulatorCalculator;
        this.imbalanceDetector = imbalanceDetector;
    }

    @Override
    @Transactional(readOnly = true)
    public JiraWorkloadDto getWorkload(UUID projectId) {
        List<ProjectJiraIssue> issues = jiraIssueRepository.findAllForWorkloadByProjectId(projectId);

        if (issues.isEmpty()) {
            return new JiraWorkloadDto(List.of(), 0, false, false, null);
        }

        boolean dueDateAvailable = jiraIssueRepository.existsByProjectIdAndDueDateIsNotNull(projectId);
        LocalDate today = LocalDate.now();
        Instant overdueThreshold = Instant.now().minus(OVERDUE_FALLBACK_DAYS, ChronoUnit.DAYS);

        JiraWorkloadCalculator.Result result = accumulatorCalculator.calculate(
                issues, dueDateAvailable, today, overdueThreshold);

        WorkloadImbalanceDetector.Result imbalance = imbalanceDetector.detect(result.members());

        return new JiraWorkloadDto(
                result.members(),
                result.unassignedCount(),
                dueDateAvailable,
                imbalance.detected(),
                imbalance.message());
    }
}
