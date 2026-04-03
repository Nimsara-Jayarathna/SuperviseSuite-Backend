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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final String JIRA_SEARCH_PATH =
            "/ex/jira/{cloudId}/rest/api/3/search/jql";

        private static final String JIRA_FIELDS_PATH =
            "/ex/jira/{cloudId}/rest/api/3/field";

    private static final String JQL_FALLBACK = "project IS NOT EMPTY ORDER BY created DESC";

        private static final String BASE_FIELDS =
            "summary,issuetype,status,assignee,duedate,updated,parent";

        private static final String STORY_POINTS_NAME = "story points";
        private static final String STORY_POINT_ESTIMATE_NAME = "story point estimate";

    private static final int PAGE_SIZE = 100;

    private static final DateTimeFormatter JIRA_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                    .withZone(ZoneOffset.UTC);

    private final RestClient restClient;
    private final JiraTokenEncryptionService jiraTokenEncryptionService;
    private final Map<String, List<String>> storyPointsFieldCacheByCloudId = new ConcurrentHashMap<>();

    JiraIssueClientImpl(
            RestClient.Builder restClientBuilder,
            JiraTokenEncryptionService jiraTokenEncryptionService) {
        this.restClient = restClientBuilder.build();
        this.jiraTokenEncryptionService = jiraTokenEncryptionService;
    }

    @Override
    public List<JiraIssueData> fetchProjectIssues(ProjectJiraIntegration integration) {
        String cloudId = integration.getCloudId();
        String jql = buildJql(integration.getJiraProjectKey());
        String bearerToken = jiraTokenEncryptionService.decrypt(integration.getAccessTokenEncrypted());
        List<String> storyPointsFieldIds = resolveStoryPointsFieldIds(cloudId, bearerToken);
        String fieldsParam = buildFieldsParam(storyPointsFieldIds);

        List<JiraIssueData> allIssues = new ArrayList<>();
        int startAt = 0;

        try {
            while (true) {
                final int currentStartAt = startAt;

                JsonNode response = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("api.atlassian.com")
                        .path(JIRA_SEARCH_PATH)
                        .queryParam("jql", jql)
                            .queryParam("fields", fieldsParam)
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

                if (currentStartAt == 0 && total == 0 && returnedCount == 0) {
                    LOGGER.debug("Jira returned no issues for cloudId={}", cloudId);
                    return List.of();
                }

                LOGGER.debug(
                        "Fetched Jira issues page: startAt={}, returned={}, total={}",
                        currentStartAt, returnedCount, total);

                if (!issuesNode.isArray() || returnedCount == 0) {
                    break;
                }

                for (JsonNode issueNode : issuesNode) {
                    JiraIssueData issue = parseIssue(issueNode, storyPointsFieldIds);
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
            throw new ServiceUnavailableException(buildJiraIssueFetchMessage(exception), exception);
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
    * @param storyPointsFieldIds story points custom field IDs discovered for the Jira cloud
     * @return the parsed issue, or {@code null} if the node is null or missing
     */
    private JiraIssueData parseIssue(JsonNode node, List<String> storyPointsFieldIds) {
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
        JsonNode statusCategoryNode = statusNode.path("statusCategory");
        String statusCategory = trimToNull(statusCategoryNode.path("key").asText(null));
        if (statusCategory == null) {
            statusCategory = trimToNull(statusCategoryNode.path("name").asText(null));
        }

        JsonNode assigneeNode = fields.path("assignee");
        String assigneeAccountId = assigneeNode.path("accountId").asText(null);
        String assigneeDisplayName = assigneeNode.path("displayName").asText(null);

        Double storyPoints = parseStoryPoints(issueKey, fields, storyPointsFieldIds);
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

    private Double parseStoryPoints(String issueKey, JsonNode fields, List<String> storyPointsFieldIds) {
        JsonNode storyPointsNode = firstAvailableStoryPointsNode(fields, storyPointsFieldIds);
        if (storyPointsNode == null || storyPointsNode.isMissingNode() || storyPointsNode.isNull()) {
            return null;
        }

        if (storyPointsNode.isNumber()) {
            return storyPointsNode.asDouble();
        }

        String raw = trimToNull(storyPointsNode.asText(null));
        if (raw == null) {
            return null;
        }

        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            LOGGER.debug("Could not parse story points '{}' for issue {}", raw, issueKey);
            return null;
        }
    }

    private JsonNode firstAvailableStoryPointsNode(JsonNode fields, List<String> storyPointsFieldIds) {
        if (fields == null || fields.isMissingNode() || fields.isNull()) {
            return null;
        }

        for (String fieldId : storyPointsFieldIds) {
            JsonNode candidate = fields.path(fieldId);
            if (!candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }

        return null;
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

    private String buildJiraIssueFetchMessage(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return "Jira authorization is no longer valid. Disconnect and reconnect Jira for this project.";
        }

        String body = trimToNull(exception.getResponseBodyAsString());
        if (body != null) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(body);
                JsonNode errorMessages = node.path("errorMessages");
                if (errorMessages.isArray() && !errorMessages.isEmpty()) {
                    String first = trimToNull(errorMessages.get(0).asText(null));
                    if (first != null) {
                        return "Failed to fetch Jira issues: " + first;
                    }
                }
                String message = trimToNull(node.path("message").asText(null));
                if (message != null) {
                    return "Failed to fetch Jira issues: " + message;
                }
            } catch (Exception ignored) {
            }
        }

        return "Failed to fetch Jira issues from Jira API (HTTP " + status + ").";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeLower(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "";
        }
        return trimmed.toLowerCase();
    }

    private String buildFieldsParam(List<String> storyPointsFieldIds) {
        if (storyPointsFieldIds == null || storyPointsFieldIds.isEmpty()) {
            return BASE_FIELDS;
        }

        StringBuilder builder = new StringBuilder(BASE_FIELDS);
        for (String fieldId : storyPointsFieldIds) {
            builder.append(',').append(fieldId);
        }
        return builder.toString();
    }

    private List<String> resolveStoryPointsFieldIds(String cloudId, String bearerToken) {
        return storyPointsFieldCacheByCloudId.computeIfAbsent(
                cloudId,
                key -> discoverStoryPointsFieldIdsFromJira(key, bearerToken));
    }

    private List<String> discoverStoryPointsFieldIdsFromJira(String cloudId, String bearerToken) {
        try {
            JsonNode fieldsResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.atlassian.com")
                            .path(JIRA_FIELDS_PATH)
                            .build(cloudId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);

            if (fieldsResponse == null || !fieldsResponse.isArray()) {
                return List.of();
            }

            Set<String> discovered = new LinkedHashSet<>();
            for (JsonNode field : fieldsResponse) {
                String fieldId = trimToNull(field.path("id").asText(null));
                if (fieldId == null || !fieldId.startsWith("customfield_")) {
                    continue;
                }

                String fieldName = normalizeLower(field.path("name").asText(null));
                if (!STORY_POINTS_NAME.equals(fieldName) && !STORY_POINT_ESTIMATE_NAME.equals(fieldName)) {
                    continue;
                }

                JsonNode schema = field.path("schema");
                String schemaType = normalizeLower(schema.path("type").asText(null));
                String schemaCustom = normalizeLower(schema.path("custom").asText(null));
                boolean numericType = "number".equals(schemaType) || "float".equals(schemaType);
                boolean likelyStoryPointsSchema = schemaCustom.contains("float")
                        || schemaCustom.contains("story")
                        || schemaCustom.contains("point");

                if (numericType || likelyStoryPointsSchema) {
                    discovered.add(fieldId);
                }
            }

            if (discovered.isEmpty()) {
                LOGGER.info("No Jira story points custom fields discovered for cloudId={}", cloudId);
                return List.of();
            }

            List<String> resolved = List.copyOf(discovered);
            LOGGER.info("Resolved Jira story points fields for cloudId={}: {}", cloudId, resolved);
            return resolved;
        } catch (RestClientResponseException exception) {
            LOGGER.warn(
                    "Failed to discover Jira story points fields for cloudId={} (HTTP {}). Proceeding without explicit story point field IDs.",
                    cloudId,
                    exception.getStatusCode().value());
            return List.of();
        } catch (ResourceAccessException exception) {
            LOGGER.warn(
                    "Could not reach Jira field metadata endpoint for cloudId={}. Proceeding without explicit story point field IDs.",
                    cloudId);
            return List.of();
        }
    }

    private String buildJql(String jiraProjectKey) {
        String key = trimToNull(jiraProjectKey);
        if (key == null) {
            return JQL_FALLBACK;
        }
        String escaped = key.replace("\\", "\\\\").replace("\"", "\\\"");
        return "project = \"" + escaped + "\" ORDER BY created DESC";
    }
}
