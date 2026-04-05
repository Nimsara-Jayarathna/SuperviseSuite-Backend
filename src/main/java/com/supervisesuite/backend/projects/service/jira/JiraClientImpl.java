package com.supervisesuite.backend.projects.service.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
class JiraClientImpl implements JiraClient {

    private static final int PAGE_SIZE = 100;
    private static final String FIELDS =
            "summary,issuetype,status,assignee,priority,duedate,resolutiondate," +
            "created,updated,parent,customfield_10016";

    private final RestClient restClient;

    JiraClientImpl(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public List<JiraIssueDto> fetchAllIssues(String cloudId, String accessToken) {
        List<JiraIssueDto> allIssues = new ArrayList<>();
        int startAt = 0;

        while (true) {
            JiraSearchResponse page = fetchPage(cloudId, accessToken, startAt);

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

    private JiraSearchResponse fetchPage(String cloudId, String accessToken, int startAt) {
        String uri = "https://api.atlassian.com/ex/jira/" + cloudId
                + "/rest/api/3/search"
                + "?fields=" + FIELDS
                + "&startAt=" + startAt
                + "&maxResults=" + PAGE_SIZE;

        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JiraSearchResponse.class);
        } catch (ResourceAccessException ex) {
            throw new ServiceUnavailableException(
                    "Unable to reach Jira API. Check network connectivity.", ex);
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException(
                    "Jira API returned an error: " + ex.getStatusCode(), ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record JiraSearchResponse(
            int total,
            int startAt,
            int maxResults,
            List<JiraIssueDto> issues) {}
}
