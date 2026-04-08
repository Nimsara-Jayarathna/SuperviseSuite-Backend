package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultJiraIssueMapperTest {

    private final DefaultJiraIssueMapper mapper = new DefaultJiraIssueMapper();

    @Test
    void mapToEntity_mapsFieldsAndParsesDates() {
        UUID projectId = UUID.randomUUID();
        Instant syncedAt = Instant.parse("2026-04-07T12:34:56Z");

        JiraIssueDto dto = new JiraIssueDto();
        dto.setKey("PROJ-42");

        JiraIssueDto.Fields fields = new JiraIssueDto.Fields();
        fields.setSummary("Implement refresh button");
        fields.setDueDate("2026-04-30");
        fields.setResolutionDate("2026-04-20T10:11:12.000+0000");
        fields.setCreated("2026-04-07T09:00:00.000+0000");
        fields.setUpdated("2026-04-07T09:10:00.000+0000");
        fields.setStoryPoints(5.0);

        JiraIssueDto.IssueType issueType = new JiraIssueDto.IssueType();
        issueType.setName("Bug");
        fields.setIssueType(issueType);

        JiraIssueDto.StatusCategory category = new JiraIssueDto.StatusCategory();
        category.setKey("indeterminate");
        JiraIssueDto.Status status = new JiraIssueDto.Status();
        status.setName("In Progress");
        status.setStatusCategory(category);
        fields.setStatus(status);

        JiraIssueDto.Assignee assignee = new JiraIssueDto.Assignee();
        assignee.setAccountId("acc-1");
        assignee.setDisplayName("Jane Doe");
        fields.setAssignee(assignee);

        JiraIssueDto.Priority priority = new JiraIssueDto.Priority();
        priority.setName("Highest");
        fields.setPriority(priority);

        JiraIssueDto.Parent parent = new JiraIssueDto.Parent();
        parent.setKey("PROJ-1");
        fields.setParent(parent);

        JiraIssueDto.Sprint sprint1 = new JiraIssueDto.Sprint();
        sprint1.setId(101L);
        sprint1.setName("Sprint 1");
        sprint1.setState("closed");
        sprint1.setStartDate("2026-04-01T09:00:00.000+0000");
        sprint1.setEndDate("2026-04-10T09:00:00.000+0000");

        JiraIssueDto.Sprint sprint2 = new JiraIssueDto.Sprint();
        sprint2.setId(102L);
        sprint2.setName("Sprint 2");
        sprint2.setState("active");
        sprint2.setStartDate("2026-04-11T09:00:00.000+0000");
        sprint2.setEndDate("2026-04-20T09:00:00.000+0000");
        fields.setSprints(List.of(sprint1, sprint2));

        dto.setFields(fields);

        ProjectJiraIssue entity = new ProjectJiraIssue();
        mapper.mapToEntity(entity, dto, projectId, syncedAt);

        assertThat(entity.getProjectId()).isEqualTo(projectId);
        assertThat(entity.getIssueKey()).isEqualTo("PROJ-42");
        assertThat(entity.getSummary()).isEqualTo("Implement refresh button");
        assertThat(entity.getIssueType()).isEqualTo("Bug");
        assertThat(entity.getStatusName()).isEqualTo("In Progress");
        assertThat(entity.getStatusCategoryKey()).isEqualTo("indeterminate");
        assertThat(entity.getAssigneeAccountId()).isEqualTo("acc-1");
        assertThat(entity.getAssigneeDisplayName()).isEqualTo("Jane Doe");
        assertThat(entity.getPriorityName()).isEqualTo("Highest");
        assertThat(entity.getStoryPoints()).isNotNull();
        assertThat(entity.getDueDate()).isEqualTo(LocalDate.parse("2026-04-30"));
        assertThat(entity.getResolutionDate()).isNotNull();
        assertThat(entity.getJiraCreatedAt()).isNotNull();
        assertThat(entity.getJiraUpdatedAt()).isNotNull();
        assertThat(entity.getParentKey()).isEqualTo("PROJ-1");
        assertThat(entity.getSprintId()).isEqualTo(102L);
        assertThat(entity.getSprintName()).isEqualTo("Sprint 2");
        assertThat(entity.getSprintState()).isEqualTo("active");
        assertThat(entity.getSprintStartDate()).isNotNull();
        assertThat(entity.getSprintEndDate()).isNotNull();
        assertThat(entity.getSyncedAt()).isEqualTo(syncedAt);
    }

    @Test
    void mapToEntity_withInvalidDates_setsDateFieldsToNull() {
        UUID projectId = UUID.randomUUID();
        Instant syncedAt = Instant.parse("2026-04-07T12:34:56Z");

        JiraIssueDto dto = new JiraIssueDto();
        dto.setKey("PROJ-404");

        JiraIssueDto.Fields fields = new JiraIssueDto.Fields();
        fields.setDueDate("invalid-date");
        fields.setResolutionDate("invalid-resolution");
        fields.setCreated("still-invalid");
        fields.setUpdated("also-invalid");
        dto.setFields(fields);

        ProjectJiraIssue entity = new ProjectJiraIssue();
        mapper.mapToEntity(entity, dto, projectId, syncedAt);

        assertThat(entity.getDueDate()).isNull();
        assertThat(entity.getResolutionDate()).isNull();
        assertThat(entity.getJiraCreatedAt()).isNull();
        assertThat(entity.getJiraUpdatedAt()).isNull();
    }

    @Test
    void mapToEntity_withoutSprintData_clearsExistingSprintFields() {
        UUID projectId = UUID.randomUUID();
        Instant syncedAt = Instant.parse("2026-04-07T12:34:56Z");

        JiraIssueDto dto = new JiraIssueDto();
        dto.setKey("PROJ-405");
        dto.setFields(new JiraIssueDto.Fields());

        ProjectJiraIssue entity = new ProjectJiraIssue();
        entity.setSprintId(999L);
        entity.setSprintName("Legacy Sprint");
        entity.setSprintState("active");
        entity.setSprintStartDate(Instant.parse("2026-04-01T00:00:00Z"));
        entity.setSprintEndDate(Instant.parse("2026-04-10T00:00:00Z"));

        mapper.mapToEntity(entity, dto, projectId, syncedAt);

        assertThat(entity.getSprintId()).isNull();
        assertThat(entity.getSprintName()).isNull();
        assertThat(entity.getSprintState()).isNull();
        assertThat(entity.getSprintStartDate()).isNull();
        assertThat(entity.getSprintEndDate()).isNull();
    }
}
