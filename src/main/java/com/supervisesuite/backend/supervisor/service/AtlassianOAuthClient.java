package com.supervisesuite.backend.supervisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
class AtlassianOAuthClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;

    AtlassianOAuthClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    TokenExchangeResult exchangeAuthorizationCode(
            String tokenTargetUrl,
            String clientId,
            String clientSecret,
            String code,
            String redirectUri) {
        Map<String, Object> tokenRequest = new LinkedHashMap<>();
        tokenRequest.put("grant_type", "authorization_code");
        tokenRequest.put("client_id", clientId);
        tokenRequest.put("client_secret", clientSecret);
        tokenRequest.put("code", code);
        tokenRequest.put("redirect_uri", redirectUri);

        Map<?, ?> tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(tokenTargetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(tokenRequest)
                    .retrieve()
                    .body(Map.class);
        } catch (ResourceAccessException exception) {
            throw new ValidationException(
                "jiraOAuth",
                "Unable to reach Atlassian token endpoint from this environment.");
        } catch (RestClientResponseException exception) {
            throw new ValidationException("jiraOAuth", buildAtlassianTokenExchangeIssue(exception));
        }

        String accessToken = tokenResponse == null
                ? null
                : NormalizationUtils.trimToNull(String.valueOf(tokenResponse.get("access_token")));
        if (accessToken == null || "null".equalsIgnoreCase(accessToken)) {
            throw new ValidationException("jiraOAuth", "Atlassian token exchange returned no access token.");
        }
        String scopes = tokenResponse == null
                ? null
                : NormalizationUtils.trimToNull(String.valueOf(tokenResponse.get("scope")));
        String refreshToken = tokenResponse == null
                ? null
                : NormalizationUtils.trimToNull(String.valueOf(tokenResponse.get("refresh_token")));
        Number expiresInSecs = tokenResponse == null || tokenResponse.get("expires_in") == null
                ? null
                : (Number) tokenResponse.get("expires_in");
        Instant tokenExpiresAt = null;
        if (expiresInSecs != null) {
             tokenExpiresAt = Instant.now().plusSeconds(expiresInSecs.longValue());
        }

        return new TokenExchangeResult(accessToken, refreshToken, tokenExpiresAt, scopes);
    }

    List<Map<String, Object>> getAccessibleResources(String accessToken) {
        List<Map<String, Object>> resources;
        try {
            resources = restClient.get()
                    .uri("https://api.atlassian.com/oauth/token/accessible-resources")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(List.class);
        } catch (ResourceAccessException exception) {
            throw new ValidationException(
                "jiraOAuth",
                "Unable to reach Atlassian API from this environment.");
        } catch (RestClientResponseException exception) {
            throw new ValidationException("jiraOAuth", "Unable to fetch Jira workspace details.");
        }

        if (resources == null || resources.isEmpty()) {
            throw new ValidationException("jiraOAuth", "No Jira workspace was returned for this authorization.");
        }

        return resources;
    }

    private String buildAtlassianTokenExchangeIssue(RestClientResponseException exception) {
        String body = NormalizationUtils.trimToNull(exception.getResponseBodyAsString());
        if (body != null) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(body);
                String error = NormalizationUtils.trimToNull(node.path("error").asText(null));
                String errorDescription = NormalizationUtils.trimToNull(node.path("error_description").asText(null));
                if (error != null && errorDescription != null) {
                    return "Atlassian token exchange failed: " + error + " (" + errorDescription + ")";
                }
                if (error != null) {
                    return "Atlassian token exchange failed: " + error;
                }
            } catch (Exception ignored) {
            }
        }
        return "Atlassian token exchange failed (HTTP " + exception.getStatusCode().value() + ").";
    }

    record TokenExchangeResult(
            String accessToken,
            String refreshToken,
            Instant tokenExpiresAt,
            String scopes) {
    }
}
