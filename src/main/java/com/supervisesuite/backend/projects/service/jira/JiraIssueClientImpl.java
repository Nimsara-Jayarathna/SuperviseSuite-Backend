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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Fetches all Jira issues for a connected project by calling the Jira REST API.
 *
 * <p>Uses the POST /rest/api/3/issue/search endpoint (Jira Cloud deprecated the GET
 * /rest/api/3/search endpoint — it now returns 410 Gone). Handles pagination transparently.
 * Authentication is performed using a Bearer token decrypted from the integration record.
 */
@Service
class JiraIssueClientImpl implements JiraIssueClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JiraIssueClientImpl.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * POST endpoint — the GET /rest/api/3/search returns 410 Gone on Jira Cloud.
     */
    private static final String JIRA_ISSUE_SEARCH_URL =
            "https://api.atlassian.com/ex/jira/%s/rest/api/3/issue/search";

    private static final List<String> FIELDS = List.of(
            "summary", "issuetype", "status", "assignee",
            "customfield_10016", "duedate", "updated", "parent");

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
        String url = String.format(JIRA_ISSUE_SEARCH_URL, cloudId);

        List<JiraIssueData> allIssues = new ArrayList<>();
        int startAt = 0;

        try {
            while (true) {
                Map<String, Object> requestBody = buildRequestBody(startAt);

                LOGGER.debug("POST Jira issue search: url={} startAt={}", url, startAt);

                JsonNode response = restClient.post()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(JsonNode.class);

                if (response == null) {
                    break;
                }

                int total = response.path("total").asInt(0);
                JsonNode issuesNode = response.path("issues");
                int returnedCount = issuesNode.isArray() ? issuesNode.size() : 0;

                if (startAt == 0 && total == 0) {
                    LOGGER.debug("Jira returned no issues for cloudId={}", cloudId);
                    return List.of();
                }

                LOGGER.debug(
                        "Fetched Jira issues page: startAt={}, returned={}, total={}",
                        startAt, returnedCount, total);

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
            int status = exception.getStatusCode().value();
            String body = exception.getResponseBodyAsString();
            LOGGER.error("Jira API returned HTTP {} for cloudId={}: {}", status, cloudId, body);
            throw new ServiceUnavailableException(buildErrorMessage(status, body), exception);
        } catch (ResourceAccessException exception) {
            LOGGER.error("Could not reach Jira for cloudId={}: {}", cloudId, exception.getMessage());
            throw new ServiceUnavailableException(
                    "Could not reach Jira. Please check your connection.", exception);
        }

        LOGGER.info("Fetched {} Jira issues total for cloudId={}", allIssues.size(), cloudId);
        return allIssues;
    }

    private Map<String, Object> buildRequestBody(int startAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jql", "ORDER BY created DESC");
        body.put("fields", FIELDS);
        body.put("maxResults", PAGE_SIZE);
        body.put("startAt", startAt);
        return body;
    }

    private String buildErrorMessage(int status, String body) {
        if (status == 401) {
            return "Jira access token is invalid or expired. Please reconnect Jira.";
        }
        if (status == 403) {
            return "Jira access denied. Ensure the OAuth scope includes read:jira-work.";
        }
        if (status == 400) {
            String jiraError = extractJiraErrorMessage(body);
            return "Jira rejected the request"
                    + (jiraError != null ? ": " + jiraError : ".")
                    + " Please reconnect Jira.";
        }
        if (status == 410) {
            return "Jira API endpoint is no longer available. Please reconnect Jira.";
        }
        return "Jira API returned HTTP " + status + ". Please reconnect Jira if the issue persists.";
    }

    private String extractJiraErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode messages = root.path("errorMessages");
            if (messages.isArray() && !messages.isEmpty()) {
                return messages.get(0).asText(null);
            }
            JsonNode errors = root.path("errors");
            if (errors.isObject() && errors.size() > 0) {
                return errors.fields().next().getValue().asText(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

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

        Double storyPoints = parseStoryPoints(fields.path("customfield_10016"));
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

    private Double parseStoryPoints(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asDouble();
    }

    private LocalDate parseDueDate(String issueKey, String rawDueDate) {
        if (rawDueDate == null || rawDueDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDueDate);
        } catch (Exception exception) {
            LOGGER.warn("Could not parse dueDate '{}' for issue {}: {}",
                    rawDueDate, issueKey, exception.getMessage());
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
            LOGGER.warn("Could not parse updated '{}' for issue {}: {}",
                    rawUpdated, issueKey, exception.getMessage());
            return null;
        }
    }
}
