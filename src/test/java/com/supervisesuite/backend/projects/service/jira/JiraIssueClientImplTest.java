package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class JiraIssueClientImplTest {

    private JiraTokenEncryptionService jiraTokenEncryptionService;
    private MockRestServiceServer mockServer;
    private JiraIssueClientImpl client;

    @BeforeEach
    void setUp() {
        jiraTokenEncryptionService = mock(JiraTokenEncryptionService.class);
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new JiraIssueClientImpl(builder, jiraTokenEncryptionService);
    }

    @Test
    void fetchProjectIssues_discoversStoryPointFieldAndParsesDecimalValues() {
        ProjectJiraIntegration integration = integration("cloud-1", "enc-token", "SCRUM");
        when(jiraTokenEncryptionService.decrypt("enc-token")).thenReturn("bearer-token");

        mockServer.expect(requestTo("https://api.atlassian.com/ex/jira/cloud-1/rest/api/3/field"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer bearer-token"))
                .andRespond(withSuccess(
                        """
                        [
                          {
                            "id": "customfield_12345",
                            "name": "Story point estimate",
                            "schema": {
                              "type": "number",
                              "custom": "com.atlassian.jira.plugin.system.customfieldtypes:float"
                            }
                          }
                        ]
                        """,
                        MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(startsWith("https://api.atlassian.com/ex/jira/cloud-1/rest/api/3/search/jql")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer bearer-token"))
          .andExpect(request -> {
              String decoded = URLDecoder.decode(request.getURI().toString(), StandardCharsets.UTF_8);
              assertThat(decoded).contains("jql=project = \"SCRUM\" ORDER BY created DESC");
              assertThat(decoded).contains("fields=summary,issuetype,status,assignee,duedate,updated,parent,customfield_12345");
          })
                .andRespond(withSuccess(
                        """
                        {
                          "total": 1,
                          "issues": [
                            {
                              "key": "SCRUM-10",
                              "fields": {
                                "summary": "Implement workload",
                                "issuetype": {"name": "Story", "subtask": false},
                                "status": {
                                  "name": "Done",
                                  "statusCategory": {"key": "done", "name": "Done"}
                                },
                                "assignee": {"accountId": "acc-1", "displayName": "Alice"},
                                "customfield_12345": 16.5,
                                "updated": "2026-04-03T10:00:00.000+0000"
                              }
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        List<JiraIssueData> issues = client.fetchProjectIssues(integration);

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getIssueKey()).isEqualTo("SCRUM-10");
        assertThat(issues.getFirst().getStoryPoints()).isEqualTo(16.5);
        mockServer.verify();
    }

    @Test
    void fetchProjectIssues_whenFieldDiscoveryFails_fallsBackToBaseFields() {
        ProjectJiraIntegration integration = integration("cloud-2", "enc-token", "SCRUM");
        when(jiraTokenEncryptionService.decrypt("enc-token")).thenReturn("bearer-token");

        mockServer.expect(requestTo("https://api.atlassian.com/ex/jira/cloud-2/rest/api/3/field"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("{}"));

        mockServer.expect(requestTo(startsWith("https://api.atlassian.com/ex/jira/cloud-2/rest/api/3/search/jql")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer bearer-token"))
          .andExpect(request -> {
              String decoded = URLDecoder.decode(request.getURI().toString(), StandardCharsets.UTF_8);
              assertThat(decoded).contains("fields=summary,issuetype,status,assignee,duedate,updated,parent");
          })
                .andRespond(withSuccess(
                        """
                        {
                          "total": 1,
                          "issues": [
                            {
                              "key": "SCRUM-20",
                              "fields": {
                                "summary": "No SP field in response",
                                "issuetype": {"name": "Task", "subtask": false},
                                "status": {
                                  "name": "To Do",
                                  "statusCategory": {"key": "new", "name": "To Do"}
                                },
                                "assignee": {"accountId": "acc-2", "displayName": "Bob"},
                                "updated": "2026-04-03T10:00:00.000+0000"
                              }
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        List<JiraIssueData> issues = client.fetchProjectIssues(integration);

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getStoryPoints()).isNull();
        mockServer.verify();
    }

    @Test
    void fetchProjectIssues_retriesFieldDiscoveryAfterPriorEmptyResultForSameCloud() {
        ProjectJiraIntegration integration = integration("cloud-cache", "enc-token", "SCRUM");
        when(jiraTokenEncryptionService.decrypt("enc-token")).thenReturn("bearer-token");

        // First fetch: discovery fails and query falls back to base fields.
        mockServer.expect(requestTo("https://api.atlassian.com/ex/jira/cloud-cache/rest/api/3/field"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("{}"));

        mockServer.expect(requestTo(startsWith("https://api.atlassian.com/ex/jira/cloud-cache/rest/api/3/search/jql")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> {
                    String decoded = URLDecoder.decode(request.getURI().toString(), StandardCharsets.UTF_8);
                    assertThat(decoded).contains("fields=summary,issuetype,status,assignee,duedate,updated,parent");
                })
                .andRespond(withSuccess(
                        """
                        {
                          "total": 0,
                          "issues": []
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        // Second fetch for same cloud: discovery should run again and include discovered SP field.
        mockServer.expect(requestTo("https://api.atlassian.com/ex/jira/cloud-cache/rest/api/3/field"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        [
                          {
                            "id": "customfield_77777",
                            "name": "Story Points",
                            "schema": {
                              "type": "number",
                              "custom": "com.atlassian.jira.plugin.system.customfieldtypes:float"
                            }
                          }
                        ]
                        """,
                        MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(startsWith("https://api.atlassian.com/ex/jira/cloud-cache/rest/api/3/search/jql")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> {
                    String decoded = URLDecoder.decode(request.getURI().toString(), StandardCharsets.UTF_8);
                    assertThat(decoded).contains("customfield_77777");
                })
                .andRespond(withSuccess(
                        """
                        {
                          "total": 1,
                          "issues": [
                            {
                              "key": "SCRUM-88",
                              "fields": {
                                "summary": "Recovered SP field",
                                "issuetype": {"name": "Story", "subtask": false},
                                "status": {
                                  "name": "In Progress",
                                  "statusCategory": {"key": "indeterminate", "name": "In Progress"}
                                },
                                "assignee": {"accountId": "acc-retry", "displayName": "Retry User"},
                                "customfield_77777": 8,
                                "updated": "2026-04-03T10:00:00.000+0000"
                              }
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

                    client.fetchProjectIssues(integration);

        List<JiraIssueData> issues = client.fetchProjectIssues(integration);

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getStoryPoints()).isEqualTo(8.0);
        mockServer.verify();
    }

    @Test
    void fetchProjectIssues_parsesStringStoryPointValuesFromDiscoveredField() {
        ProjectJiraIntegration integration = integration("cloud-3", "enc-token", "SCRUM");
        when(jiraTokenEncryptionService.decrypt("enc-token")).thenReturn("bearer-token");

        mockServer.expect(requestTo("https://api.atlassian.com/ex/jira/cloud-3/rest/api/3/field"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        [
                          {
                            "id": "customfield_55555",
                            "name": "Story Points",
                            "schema": {
                              "type": "number",
                              "custom": "com.atlassian.jira.plugin.system.customfieldtypes:float"
                            }
                          }
                        ]
                        """,
                        MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(startsWith("https://api.atlassian.com/ex/jira/cloud-3/rest/api/3/search/jql")))
                .andExpect(method(HttpMethod.GET))
          .andExpect(request -> {
              String decoded = URLDecoder.decode(request.getURI().toString(), StandardCharsets.UTF_8);
              assertThat(decoded).contains("fields=summary,issuetype,status,assignee,duedate,updated,parent,customfield_55555");
          })
                .andRespond(withSuccess(
                        """
                        {
                          "total": 1,
                          "issues": [
                            {
                              "key": "SCRUM-30",
                              "fields": {
                                "summary": "String SP",
                                "issuetype": {"name": "Story", "subtask": false},
                                "status": {
                                  "name": "In Progress",
                                  "statusCategory": {"key": "indeterminate", "name": "In Progress"}
                                },
                                "assignee": {"accountId": "acc-3", "displayName": "Carol"},
                                "customfield_55555": "26",
                                "updated": "2026-04-03T10:00:00.000+0000"
                              }
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        List<JiraIssueData> issues = client.fetchProjectIssues(integration);

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getStoryPoints()).isEqualTo(26.0);
        mockServer.verify();
    }

          @Test
          void fetchProjectIssues_whenJiraReturnsUnauthorized_throwsValidationException() {
        ProjectJiraIntegration integration = integration("cloud-4", "enc-token", "SCRUM");
        when(jiraTokenEncryptionService.decrypt("enc-token")).thenReturn("bearer-token");

        mockServer.expect(requestTo("https://api.atlassian.com/ex/jira/cloud-4/rest/api/3/field"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(startsWith("https://api.atlassian.com/ex/jira/cloud-4/rest/api/3/search/jql")))
          .andExpect(method(HttpMethod.GET))
          .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"errorMessages\":[\"Unauthorized\"]}"));

        assertThatThrownBy(() -> client.fetchProjectIssues(integration))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("Jira authorization is no longer valid");

        mockServer.verify();
          }

    private static ProjectJiraIntegration integration(String cloudId, String encryptedToken, String projectKey) {
        ProjectJiraIntegration integration = new ProjectJiraIntegration();
        integration.setId(UUID.randomUUID());
        integration.setProjectId(UUID.randomUUID());
        integration.setCloudId(cloudId);
        integration.setWorkspaceName("workspace");
        integration.setAccessTokenEncrypted(encryptedToken);
        integration.setConnectedAt(Instant.now());
        integration.setJiraProjectKey(projectKey);
        return integration;
    }
}
