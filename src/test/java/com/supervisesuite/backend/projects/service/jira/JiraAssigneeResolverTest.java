package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JiraAssigneeResolverTest {

    private JiraAssigneeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new JiraAssigneeResolver();
    }

    @Test
                void resolveAssigneeWorkUnits_keepsParentAndSubtasks_andStandaloneIssues() {
        JiraIssueData parentStory = issue(
                "SCRUM-100",
                false,
                null,
                "po-account",
                "Product Owner",
                "In Progress",
                5.0,
                LocalDate.now().plusDays(3),
                Instant.now());

        JiraIssueData firstSubtask = issue(
                "SCRUM-101",
                true,
                "SCRUM-100",
                "student-a",
                "Alice",
                "In Progress",
                2.0,
                LocalDate.now().plusDays(2),
                Instant.now());

        JiraIssueData secondSubtask = issue(
                "SCRUM-102",
                true,
                "SCRUM-100",
                "student-b",
                "Bob",
                "Done",
                3.0,
                LocalDate.now().minusDays(1),
                Instant.now());

        JiraIssueData standaloneStory = issue(
                "SCRUM-200",
                false,
                null,
                "student-c",
                "Carol",
                "In Progress",
                1.0,
                null,
                Instant.now());

        List<JiraIssueData> result = resolver.resolveAssigneeWorkUnits(
                List.of(parentStory, firstSubtask, secondSubtask, standaloneStory));

        assertThat(result)
                .extracting(JiraIssueData::getIssueKey)
                .containsExactly("SCRUM-100", "SCRUM-101", "SCRUM-102", "SCRUM-200");
    }

    @Test
    void resolveAssigneeWorkUnits_deduplicatesDuplicateIssueKeys() {
        JiraIssueData first = issue(
                "SCRUM-400",
                false,
                null,
                "student-a",
                "Alice",
                "In Progress",
                3.0,
                LocalDate.now().plusDays(1),
                Instant.now());

        JiraIssueData duplicate = issue(
                "SCRUM-400",
                false,
                null,
                "student-a",
                "Alice",
                "Done",
                3.0,
                LocalDate.now().plusDays(1),
                Instant.now());

        JiraIssueData another = issue(
                "SCRUM-401",
                true,
                "SCRUM-400",
                "student-a",
                "Alice",
                "In Progress",
                1.0,
                LocalDate.now().plusDays(2),
                Instant.now());

        List<JiraIssueData> result = resolver.resolveAssigneeWorkUnits(List.of(first, duplicate, another));

        assertThat(result)
                .extracting(JiraIssueData::getIssueKey)
                .containsExactly("SCRUM-400", "SCRUM-401");
    }

    @Test
    void resolveAssigneeWorkUnits_keepsUnassignedIssues() {
        JiraIssueData unassignedStandalone = issue(
                "SCRUM-300",
                false,
                null,
                null,
                null,
                "To Do",
                null,
                null,
                Instant.now());

        List<JiraIssueData> result = resolver.resolveAssigneeWorkUnits(List.of(unassignedStandalone));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getIssueKey()).isEqualTo("SCRUM-300");
        assertThat(result.getFirst().getAssigneeAccountId()).isNull();
    }

    private static JiraIssueData issue(
            String key,
            boolean subtask,
            String parentKey,
            String accountId,
            String displayName,
            String statusCategory,
            Double storyPoints,
            LocalDate dueDate,
            Instant lastUpdated) {
        return new JiraIssueData(
                key,
                "Summary " + key,
                subtask ? "Subtask" : "Story",
                subtask,
                parentKey,
                statusCategory,
                statusCategory,
                accountId,
                displayName,
                storyPoints,
                dueDate,
                lastUpdated);
    }
}