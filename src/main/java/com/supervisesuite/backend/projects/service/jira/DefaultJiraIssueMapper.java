package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class DefaultJiraIssueMapper implements JiraIssueMapper {

    private static final DateTimeFormatter JIRA_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Override
    public void mapToEntity(ProjectJiraIssue issue, JiraIssueDto dto, UUID projectId, Instant syncedAt) {
        issue.setProjectId(projectId);
        issue.setIssueKey(dto.getKey());
        issue.setSyncedAt(syncedAt);

        JiraIssueDto.Fields fields = dto.getFields();
        if (fields == null) {
            return;
        }

        issue.setSummary(fields.getSummary());

        JiraIssueDto.IssueType issueType = fields.getIssueType();
        issue.setIssueType(issueType != null ? issueType.getName() : null);

        JiraIssueDto.Status status = fields.getStatus();
        if (status != null) {
            issue.setStatusName(status.getName());
            JiraIssueDto.StatusCategory category = status.getStatusCategory();
            issue.setStatusCategoryKey(category != null ? category.getKey() : null);
        }

        JiraIssueDto.Assignee assignee = fields.getAssignee();
        if (assignee != null) {
            issue.setAssigneeAccountId(assignee.getAccountId());
            issue.setAssigneeDisplayName(assignee.getDisplayName());
        }

        JiraIssueDto.Priority priority = fields.getPriority();
        issue.setPriorityName(priority != null ? priority.getName() : null);

        Double storyPoints = fields.getStoryPoints();
        issue.setStoryPoints(storyPoints != null ? BigDecimal.valueOf(storyPoints) : null);

        issue.setDueDate(parseLocalDate(fields.getDueDate()));
        issue.setResolutionDate(parseInstant(fields.getResolutionDate()));
        issue.setJiraCreatedAt(parseInstant(fields.getCreated()));
        issue.setJiraUpdatedAt(parseInstant(fields.getUpdated()));

        JiraIssueDto.Parent parent = fields.getParent();
        issue.setParentKey(parent != null ? parent.getKey() : null);

        List<JiraIssueDto.SprintField> sprints = fields.getSprint();
        if (sprints != null && !sprints.isEmpty()) {
            JiraIssueDto.SprintField sprint = sprints.get(sprints.size() - 1);
            issue.setSprintId(sprint.getId());
            issue.setSprintName(sprint.getName());
            issue.setSprintState(sprint.getState());
            issue.setSprintStartDate(parseInstant(sprint.getStartDate()));
            issue.setSprintEndDate(parseInstant(sprint.getEndDate()));
        }
    }

    private static LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, JIRA_TIMESTAMP_FORMAT).toInstant();
        } catch (Exception ex) {
            try {
                return Instant.parse(value);
            } catch (Exception fallback) {
                return null;
            }
        }
    }
}
