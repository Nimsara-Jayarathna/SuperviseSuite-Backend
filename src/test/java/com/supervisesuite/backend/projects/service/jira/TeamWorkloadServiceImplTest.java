package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.projects.dto.TeamWorkloadResponseDto;
import com.supervisesuite.backend.projects.dto.TeamWorkloadStudentDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamWorkloadServiceImplTest {

    @Mock
    private JiraIssueClient jiraIssueClient;

    @Mock
    private JiraAssigneeResolver jiraAssigneeResolver;

    private TeamWorkloadServiceImpl service;
    private ProjectJiraIntegration integration;

    @BeforeEach
    void setUp() {
        service = new TeamWorkloadServiceImpl(jiraIssueClient, jiraAssigneeResolver);
        integration = new ProjectJiraIntegration();
        integration.setProjectId(UUID.randomUUID());
        integration.setCloudId("cloud-1");
        integration.setAccessTokenEncrypted("encrypted");
        integration.setWorkspaceName("workspace");
    }

    @Test
    void computeWorkload_emptyIssueList_returnsValidEmptyResponse() {
        when(jiraIssueClient.fetchProjectIssues(integration)).thenReturn(List.of());

        TeamWorkloadResponseDto result = service.computeWorkload(integration);

        assertThat(result.getStudents()).isEmpty();
        assertThat(result.getUnassignedIssues()).isEqualTo(0);
        assertThat(result.isImbalanceDetected()).isFalse();
        assertThat(result.getImbalanceMessage()).isNull();
        assertThat(result.isDueDateAvailable()).isFalse();
    }

    @Test
    void computeWorkload_calculatesPerStudentMetricsAndUnassignedIssues() {
        LocalDate today = LocalDate.now();

        JiraIssueData a1 = issue("SCRUM-1", false, null, "acc-alice", "Alice", "Done", 3.0,
                today.minusDays(2), nowMinusDays(5));
        JiraIssueData a2 = issue("SCRUM-2", false, null, "acc-alice", "Alice", "In Progress", 2.0,
                today.minusDays(1), nowMinusDays(1));
        JiraIssueData a3 = issue("SCRUM-3", false, null, "acc-alice", "Alice", "In Progress", null,
                null, nowMinusDays(10));

        JiraIssueData b1 = issue("SCRUM-4", false, null, "acc-bob", "Bob", "In Progress", 1.0,
                today.plusDays(4), nowMinusDays(2));

        JiraIssueData unassigned = issue("SCRUM-5", false, null, null, null, "To Do", 1.0,
                today.minusDays(3), nowMinusDays(8));

        List<JiraIssueData> rawIssues = List.of(a1, a2, a3, b1, unassigned);

        when(jiraIssueClient.fetchProjectIssues(integration)).thenReturn(rawIssues);
        when(jiraAssigneeResolver.resolveAssigneeWorkUnits(rawIssues)).thenReturn(rawIssues);

        TeamWorkloadResponseDto result = service.computeWorkload(integration);

        assertThat(result.getUnassignedIssues()).isEqualTo(1);
        assertThat(result.isDueDateAvailable()).isTrue();
        assertThat(result.isImbalanceDetected()).isFalse();
        assertThat(result.getImbalanceMessage()).isNull();
        assertThat(result.getStudents()).hasSize(2);

        TeamWorkloadStudentDto alice = result.getStudents().get(0);
        assertThat(alice.getDisplayName()).isEqualTo("Alice");
        assertThat(alice.getAccountId()).isEqualTo("acc-alice");
        assertThat(alice.getAssigned()).isEqualTo(3);
        assertThat(alice.getCompleted()).isEqualTo(1);
        assertThat(alice.getInProgress()).isEqualTo(2);
        assertThat(alice.getOverdue()).isEqualTo(2);
        assertThat(alice.getStoryPointsAssigned()).isEqualTo(5.0);
        assertThat(alice.getStoryPointsCompleted()).isEqualTo(3.0);
        assertThat(alice.getCompletionRate()).isEqualTo(33);
        assertThat(alice.getLastActiveDate())
                .isEqualTo(nowMinusDays(1).atZone(ZoneOffset.UTC).toLocalDate().toString());

        TeamWorkloadStudentDto bob = result.getStudents().get(1);
        assertThat(bob.getDisplayName()).isEqualTo("Bob");
        assertThat(bob.getAccountId()).isEqualTo("acc-bob");
        assertThat(bob.getAssigned()).isEqualTo(1);
        assertThat(bob.getCompleted()).isEqualTo(0);
        assertThat(bob.getInProgress()).isEqualTo(1);
        assertThat(bob.getOverdue()).isEqualTo(0);
        assertThat(bob.getStoryPointsAssigned()).isEqualTo(1.0);
        assertThat(bob.getCompletionRate()).isEqualTo(0);
    }

    @Test
    void computeWorkload_detectsImbalanceWhenMaxExceedsThreeTimesMin() {
        LocalDate today = LocalDate.now();

        List<JiraIssueData> rawIssues = List.of(
                issue("SCRUM-11", false, null, "acc-alice", "Alice", "In Progress", 1.0, today.plusDays(1), nowMinusDays(1)),
                issue("SCRUM-12", false, null, "acc-alice", "Alice", "In Progress", 1.0, today.plusDays(1), nowMinusDays(1)),
                issue("SCRUM-13", false, null, "acc-alice", "Alice", "In Progress", 1.0, today.plusDays(1), nowMinusDays(1)),
                issue("SCRUM-14", false, null, "acc-alice", "Alice", "In Progress", 1.0, today.plusDays(1), nowMinusDays(1)),
                issue("SCRUM-15", false, null, "acc-bob", "Bob", "In Progress", 1.0, today.plusDays(1), nowMinusDays(1)));

        when(jiraIssueClient.fetchProjectIssues(integration)).thenReturn(rawIssues);
        when(jiraAssigneeResolver.resolveAssigneeWorkUnits(rawIssues)).thenReturn(rawIssues);

        TeamWorkloadResponseDto result = service.computeWorkload(integration);

        assertThat(result.isImbalanceDetected()).isTrue();
        assertThat(result.getImbalanceMessage()).isEqualTo("Alice has 3x more open issues than Bob");
    }

    @Test
    void computeWorkload_withOnlyUnassignedResolvedIssues_returnsEmptyStudentsAndUnassignedCount() {
        LocalDate today = LocalDate.now();
        JiraIssueData unassignedOne = issue("SCRUM-21", false, null, null, null, "In Progress", 2.0,
                today.plusDays(5), nowMinusDays(2));
        JiraIssueData unassignedTwo = issue("SCRUM-22", false, null, null, null, "Done", null,
                null, nowMinusDays(1));
        List<JiraIssueData> rawIssues = List.of(unassignedOne, unassignedTwo);

        when(jiraIssueClient.fetchProjectIssues(integration)).thenReturn(rawIssues);
        when(jiraAssigneeResolver.resolveAssigneeWorkUnits(rawIssues)).thenReturn(rawIssues);

        TeamWorkloadResponseDto result = service.computeWorkload(integration);

        assertThat(result.getStudents()).isEmpty();
        assertThat(result.getUnassignedIssues()).isEqualTo(2);
    }

    @Test
    void computeWorkload_appliesSubtaskAssigneeResolutionEndToEnd() {
        TeamWorkloadServiceImpl serviceWithRealResolver = new TeamWorkloadServiceImpl(jiraIssueClient, new JiraAssigneeResolver());

        LocalDate today = LocalDate.now();
        JiraIssueData parentStory = issue("SCRUM-30", false, null, "acc-po", "Product Owner", "In Progress", 5.0,
                today.plusDays(2), nowMinusDays(1));
        JiraIssueData subtask = issue("SCRUM-31", true, "SCRUM-30", "acc-student", "Student One", "In Progress", 2.0,
                today.plusDays(1), nowMinusDays(1));
        JiraIssueData standalone = issue("SCRUM-32", false, null, "acc-student-2", "Student Two", "Done", 3.0,
                today.minusDays(1), nowMinusDays(2));

        List<JiraIssueData> rawIssues = List.of(parentStory, subtask, standalone);
        when(jiraIssueClient.fetchProjectIssues(integration)).thenReturn(rawIssues);

        TeamWorkloadResponseDto result = serviceWithRealResolver.computeWorkload(integration);

        assertThat(result.getStudents()).hasSize(3);
        assertThat(result.getStudents())
                .extracting(TeamWorkloadStudentDto::getAccountId)
                .containsExactlyInAnyOrder("acc-po", "acc-student", "acc-student-2");
        assertThat(result.getStudents())
                .extracting(TeamWorkloadStudentDto::getAccountId)
                .contains("acc-po");
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

    private static Instant nowMinusDays(long days) {
        return Instant.now().minusSeconds(days * 24 * 60 * 60);
    }
}