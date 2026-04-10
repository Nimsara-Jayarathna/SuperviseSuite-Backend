package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrator service adhering to SRP that delegates sprint analysis rules to a domain component.
 */
@Service
class JiraSprintProgressServiceImpl implements JiraSprintProgressService {

    private final ProjectJiraIssueRepository jiraIssueRepository;
    private final JiraSprintProgressCalculator calculator;

    JiraSprintProgressServiceImpl(
            ProjectJiraIssueRepository jiraIssueRepository,
            JiraSprintProgressCalculator calculator) {
        this.jiraIssueRepository = jiraIssueRepository;
        this.calculator = calculator;
    }

    @Override
    @Transactional(readOnly = true)
    public JiraSprintProgressDto getSprintProgress(UUID projectId) {
        List<ProjectJiraIssue> issues = jiraIssueRepository.findAllByProjectId(projectId);
        return calculator.calculate(issues);
    }
}
