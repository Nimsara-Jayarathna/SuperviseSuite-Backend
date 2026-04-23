package com.supervisesuite.backend.projects.service.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
class JiraClientImpl implements JiraClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_RETRIES = 4;
    private static final long BASE_BACKOFF_MS = 500L;
    private static final long MAX_BACKOFF_MS = 8_000L;
    private static final List<String> BASE_FIELDS = List.of(
            "summary",
            "issuetype",
            "status",
            "assignee",
            "priority",
            "duedate",
            "resolutiondate",
            "created",
            "updated",
        "parent");
    private static final List<List<String>> FIELD_CANDIDATES = List.of(
        appendField(BASE_FIELDS, "*all"),
        appendField(BASE_FIELDS, "customfield_10016"),
        appendField(BASE_FIELDS, "customfield_10020"),
        appendField(BASE_FIELDS, "customfield_10021"),
        appendField(BASE_FIELDS, "customfield_10026"),
        appendField(appendField(BASE_FIELDS, "customfield_10016"), "customfield_10020"),
        appendField(appendField(BASE_FIELDS, "customfield_10016"), "customfield_10021"),
        BASE_FIELDS);
    private static final String DEFAULT_JQL = "issuekey IS NOT EMPTY ORDER BY updated DESC";

    private final RestClient restClient;

    JiraClientImpl(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public List<JiraIssueDto> fetchAllIssues(String cloudId, String accessToken) {
        RestClientResponseException lastBadRequest = null;

        // Prefer enhanced endpoint first (supported replacement for deprecated search routes).
        for (List<String> fields : FIELD_CANDIDATES) {
            try {
                return fetchAllIssuesEnhanced(cloudId, accessToken, fields);
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                    lastBadRequest = ex;
                    continue;
                }
                if (ex.getStatusCode() == HttpStatus.GONE || ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    break;
                }
                throw new ServiceUnavailableException(
                        "Jira API returned an error: " + summarizeResponse(ex), ex);
            } catch (ResourceAccessException ex) {
                throw new ServiceUnavailableException(
                        "Unable to reach Jira API. Check network connectivity.", ex);
            }
        }

        // Fallback to legacy endpoint for older tenants still accepting /search.
        for (List<String> fields : FIELD_CANDIDATES) {
            try {
                return fetchAllIssuesLegacy(cloudId, accessToken, fields);
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                    lastBadRequest = ex;
                    continue;
                }
                if (ex.getStatusCode() == HttpStatus.GONE || ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    continue;
                }
                throw new ServiceUnavailableException(
                        "Jira API returned an error: " + summarizeResponse(ex), ex);
            } catch (ResourceAccessException ex) {
                throw new ServiceUnavailableException(
                        "Unable to reach Jira API. Check network connectivity.", ex);
            }
        }

        if (lastBadRequest != null) {
            throw new ServiceUnavailableException(
                    "Jira API returned an error: " + summarizeResponse(lastBadRequest),
                    lastBadRequest);
        }

        throw new ServiceUnavailableException("Jira API search endpoint is unavailable.");
    }

    private List<JiraIssueDto> fetchAllIssuesEnhanced(
            String cloudId,
            String accessToken,
            List<String> fields) {
        List<JiraIssueDto> allIssues = new ArrayList<>();
        String nextPageToken = null;

        while (true) {
            EnhancedSearchRequest body = new EnhancedSearchRequest(
                    DEFAULT_JQL,
                    PAGE_SIZE,
                    fields,
                    nextPageToken);
            EnhancedSearchResponse page = fetchEnhancedPage(cloudId, accessToken, body);

            List<JiraIssueDto> issues = (page != null && page.issues() != null)
                    ? page.issues()
                    : List.of();
            if (issues.isEmpty()) {
                break;
            }

            allIssues.addAll(issues);

            if (page == null || page.isLast() || page.nextPageToken() == null || page.nextPageToken().isBlank()) {
                break;
            }
            nextPageToken = page.nextPageToken();
        }

        return allIssues;
    }

    private List<JiraIssueDto> fetchAllIssuesLegacy(
            String cloudId,
            String accessToken,
            List<String> fields) {
        List<JiraIssueDto> allIssues = new ArrayList<>();
        int startAt = 0;

        while (true) {
            LegacySearchRequest body = new LegacySearchRequest(DEFAULT_JQL, startAt, PAGE_SIZE, fields);
            LegacySearchResponse page = fetchLegacyPage(cloudId, accessToken, body);

            List<JiraIssueDto> issues = (page != null && page.issues() != null)
                    ? page.issues()
                    : List.of();

            if (issues.isEmpty()) {
                break;
            }

            allIssues.addAll(issues);
            startAt += issues.size();

            int total = page != null ? page.total() : 0;
            if (startAt >= total) {
                break;
            }
        }

        return allIssues;
    }

    private EnhancedSearchResponse fetchEnhancedPage(
            String cloudId,
            String accessToken,
            EnhancedSearchRequest body) {
        String modernUri = "https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/3/search/jql";
        int attempt = 0;

        while (true) {
            try {
                attempt++;
                return restClient.post()
                        .uri(modernUri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(EnhancedSearchResponse.class);
            } catch (RestClientResponseException ex) {
                if (attempt < MAX_RETRIES && isRetryableStatus(ex)) {
                    sleepForRetry(ex, attempt);
                    continue;
                }
                throw ex;
            }
        }
    }

    private LegacySearchResponse fetchLegacyPage(
            String cloudId,
            String accessToken,
            LegacySearchRequest body) {
        String legacyUri = "https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/3/search";
        int attempt = 0;

        while (true) {
            try {
                attempt++;
                return restClient.post()
                        .uri(legacyUri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(LegacySearchResponse.class);
            } catch (RestClientResponseException ex) {
                if (attempt < MAX_RETRIES && isRetryableStatus(ex)) {
                    sleepForRetry(ex, attempt);
                    continue;
                }
                throw ex;
            }
        }
    }

    private static boolean isRetryableStatus(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        return status == 429 || status == 503;
    }

    private static void sleepForRetry(RestClientResponseException ex, int attempt) {
        long sleepMs = parseRetryAfterMs(ex);
        if (sleepMs <= 0L) {
            long exp = Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (1L << Math.max(0, attempt - 1)));
            long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, exp / 3));
            sleepMs = Math.min(MAX_BACKOFF_MS, exp + jitter);
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw ex;
        }
    }

    private static long parseRetryAfterMs(RestClientResponseException ex) {
        String retryAfter = ex.getResponseHeaders() == null
            ? null
            : ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return -1L;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Math.max(0L, seconds) * 1000L;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static List<String> appendField(List<String> base, String field) {
        List<String> next = new ArrayList<>(base);
        next.add(field);
        return List.copyOf(next);
    }

    private static String summarizeResponse(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return String.valueOf(ex.getStatusCode());
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        if (compact.length() > 220) {
            compact = compact.substring(0, 220) + "...";
        }
        return ex.getStatusCode() + " - " + compact;
    }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private record EnhancedSearchRequest(
            String jql,
            int maxResults,
            List<String> fields,
            String nextPageToken) {}

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private record LegacySearchRequest(
            String jql,
            int startAt,
            int maxResults,
            List<String> fields) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
        private record EnhancedSearchResponse(
            boolean isLast,
            String nextPageToken,
            List<JiraIssueDto> issues) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record LegacySearchResponse(
            int total,
            int startAt,
            int maxResults,
            List<JiraIssueDto> issues) {}
}
