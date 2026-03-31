package com.supervisesuite.backend.projects.service.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Fetches all Jira issues for a connected project by calling the Jira REST API search endpoint.
 *
 * <p>Handles pagination transparently — callers receive a single flat list of all issues
 * regardless of how many pages the Jira API required. Authentication is performed using
 * a Bearer token decrypted from the integration record.
 */
@Service
class JiraIssueClientImpl implements JiraIssueClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JiraIssueClientImpl.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String JIRA_SEARCH_URL_TEMPLATE =
            "https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3/search";

    private static final String JQL = "project IS NOT EMPTY ORDER BY created DESC";

    private static final String FIELDS =
            "summary,issuetype,status,assignee,customfield_10016,duedate,updated,parent";

    private static final int PAGE_SIZE = 100;

    private static final DateTimeFormatter JIRA_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                    .withZone(ZoneOffset.UTC);

    private final RestClient restClient;
    private final JiraTokenEncryptionService jiraTokenEncryptionService;

    JiraIssueClientImpl(
            RestClient.Builder restClientBuilder,
            JiraTokenEncryptionService jiraTokenEncryptionService) {
        this.restClient = restClientBuilder.build();
        this.jiraTokenEncryptionService = jiraTokenEncryptionService;
    }

    @Override
    public List<JiraIssueData> fetchProjectIssues(ProjectJiraIntegration integration) {
        String cloudId = integration.getCloudId();
        String bearerToken = jiraTokenEncryptionService.decrypt(integration.getAccessTokenEncrypted());

        List<JiraIssueData> allIssues = new ArrayList<>();
        int startAt = 0;

        try {
            while (true) {
                final int currentStartAt = startAt;

                JsonNode response = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("api.atlassian.com")
                                .path("/ex/jira/{cloudId}/rest/api/3/search")
                                .queryParam("jql", JQL)
                                .queryParam("fields", FIELDS)
                                .queryParam("maxResults", PAGE_SIZE)
                                .queryParam("startAt", currentStartAt)
                                .build(cloudId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(JsonNode.class);

                if (response == null) {
                    break;
                }

                int total = response.path("total").asInt(0);
                JsonNode issuesNode = response.path("issues");
                int returnedCount = issuesNode.isArray() ? issuesNode.size() : 0;

                LOGGER.debug(
                        "Fetched Jira issues page: startAt={}, returned={}, total={}",
                        currentStartAt, returnedCount, total);

                if (!issuesNode.isArray() || returnedCount == 0) {
                    break;
                }

                for (JsonNode issueNode : issuesNode) {
                    JiraIssueData issue = parseIssue(issueNode);
                    if (issue != null) {
                        allIssues.add(issue);
                    }
                }

                startAt += returnedCount;
                if (startAt >= total) {
                    break;
                }
            }
        } catch (RestClientResponseException exception) {
            throw new ServiceUnavailableException(
                    "Failed to fetch Jira issues — workspace may be disconnected.", exception);
        } catch (ResourceAccessException exception) {
            throw new ServiceUnavailableException(
                    "Could not reach Jira. Please check your connection.", exception);
        }

        LOGGER.info("Fetched {} Jira issues total for cloudId={}", allIssues.size(), cloudId);
        return allIssues;
    }

    /**
     * Parses a single issue JSON node into a {@link JiraIssueData} instance.
     *
     * <p>Field-level parse failures (e.g. malformed date) are logged as warnings and
     * result in that field being {@code null} rather than aborting the entire fetch.
     *
     * @param node the JSON node representing one issue from the Jira search response
     * @return the parsed issue, or {@code null} if the node is null or missing
     */
    private JiraIssueData parseIssue(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }

        String issueKey = node.path("key").asText(null);

        JsonNode fields = node.path("fields");

        String summary = fields.path("summary").asText(null);

        JsonNode issueTypeNode = fields.path("issuetype");
        String issueType = issueTypeNode.path("name").asText(null);
        boolean subtask = issueTypeNode.path("subtask").asBoolean(false);

        String parentKey = fields.path("parent").path("key").asText(null);

        JsonNode statusNode = fields.path("status");
        String statusName = statusNode.path("name").asText(null);
        String statusCategory = statusNode.path("statusCategory").path("name").asText(null);

        JsonNode assigneeNode = fields.path("assignee");
        String assigneeAccountId = assigneeNode.path("accountId").asText(null);
        String assigneeDisplayName = assigneeNode.path("displayName").asText(null);

        Double storyPoints = parseStoryPoints(issueKey, fields.path("customfield_10016"));
        LocalDate dueDate = parseDueDate(issueKey, fields.path("duedate").asText(null));
        Instant lastUpdated = parseLastUpdated(issueKey, fields.path("updated").asText(null));

        return new JiraIssueData(
                issueKey,
                summary,
                issueType,
                subtask,
                parentKey,
                statusName,
                statusCategory,
                assigneeAccountId,
                assigneeDisplayName,
                storyPoints,
                dueDate,
                lastUpdated);
    }

    private Double parseStoryPoints(String issueKey, JsonNode storyPointsNode) {
        if (storyPointsNode == null || storyPointsNode.isMissingNode() || storyPointsNode.isNull()) {
            return null;
        }
        return storyPointsNode.asDouble();
    }

    private LocalDate parseDueDate(String issueKey, String rawDueDate) {
        if (rawDueDate == null || rawDueDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDueDate);
        } catch (Exception exception) {
            LOGGER.warn("Could not parse dueDate '{}' for issue {}: {}", rawDueDate, issueKey, exception.getMessage());
            return null;
        }
    }

    private Instant parseLastUpdated(String issueKey, String rawUpdated) {
        if (rawUpdated == null || rawUpdated.isBlank()) {
            return null;
        }
        try {
            return Instant.from(JIRA_DATE_TIME_FORMATTER.parse(rawUpdated));
        } catch (Exception exception) {
            LOGGER.warn("Could not parse updated '{}' for issue {}: {}", rawUpdated, issueKey, exception.getMessage());
            return null;
        }
    }
}
