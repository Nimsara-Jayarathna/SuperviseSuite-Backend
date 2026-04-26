package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteResultDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
class SupervisorJiraWorkspaceSelectionStore {

    private static final long JIRA_WORKSPACE_SELECTION_TTL_SECONDS = 600L;

    private final Map<String, PendingJiraWorkspaceSelection> pendingJiraWorkspaceSelections = new ConcurrentHashMap<>();

    void cleanupExpired(Instant now) {
        pendingJiraWorkspaceSelections.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    PendingJiraWorkspaceSelection get(String selectionToken) {
        return pendingJiraWorkspaceSelections.get(selectionToken);
    }

    void remove(String selectionToken) {
        pendingJiraWorkspaceSelections.remove(selectionToken);
    }

    void put(
            String pendingToken,
            UUID projectId,
            UUID userId,
            String accessToken,
            String refreshToken,
            Instant tokenExpiresAt,
            String scopes,
            List<JiraOAuthCompleteResultDto.WorkspaceOption> workspaceOptions) {
        pendingJiraWorkspaceSelections.put(
                pendingToken,
                new PendingJiraWorkspaceSelection(
                        projectId,
                        userId,
                        accessToken,
                        refreshToken,
                        tokenExpiresAt,
                        scopes,
                        workspaceOptions,
                        Instant.now().plusSeconds(JIRA_WORKSPACE_SELECTION_TTL_SECONDS)));
    }

    record PendingJiraWorkspaceSelection(
            UUID projectId,
            UUID userId,
            String accessToken,
            String refreshToken,
            Instant tokenExpiresAt,
            String scopes,
            List<JiraOAuthCompleteResultDto.WorkspaceOption> workspaceOptions,
            Instant expiresAt) {
    }
}
