package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraIssueSummaryDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JiraIssueQueryServiceImpl implements JiraIssueQueryService {

    private final ProjectJiraIssueRepository jiraIssueRepository;

    JiraIssueQueryServiceImpl(ProjectJiraIssueRepository jiraIssueRepository) {
        this.jiraIssueRepository = jiraIssueRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JiraIssueSummaryDto> getIssuesForProject(UUID projectId) {
        return jiraIssueRepository.findAllByProjectId(projectId)
                .stream()
                .map(JiraIssueQueryServiceImpl::toDto)
                .toList();
    }

    private static JiraIssueSummaryDto toDto(ProjectJiraIssue issue) {
        return new JiraIssueSummaryDto(
                issue.getIssueKey(),
                issue.getSummary(),
                issue.getIssueType(),
                issue.getStatusName(),
                issue.getStatusCategoryKey(),
                issue.getAssigneeDisplayName(),
                issue.getParentKey(),
                issue.getSprintId(),
                issue.getSprintName()
        );
    }
}
