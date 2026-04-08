package com.supervisesuite.backend.projects.dto;

import java.util.List;

public record JiraOAuthCompleteResultDto(
		String projectId,
		String workspaceName,
		boolean requiresWorkspaceSelection,
		String selectionToken,
		List<WorkspaceOption> workspaceOptions) {

	public record WorkspaceOption(String cloudId, String workspaceName, String workspaceUrl) {
	}
}
