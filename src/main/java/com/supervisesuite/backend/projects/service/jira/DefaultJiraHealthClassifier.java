package com.supervisesuite.backend.projects.service.jira;

import org.springframework.stereotype.Component;

@Component
class DefaultJiraHealthClassifier implements JiraHealthClassifier {

    /** Jira status category keys — defined by Atlassian, not configurable. */
    private static final String STATUS_TODO = "new";
    private static final String STATUS_IN_PROGRESS = "indeterminate";
    private static final String STATUS_DONE = "done";

    @Override
    public boolean isDoneStatus(String statusCategoryKey) {
        return STATUS_DONE.equals(statusCategoryKey);
    }

    @Override
    public boolean isToDoStatus(String statusCategoryKey) {
        return STATUS_TODO.equals(statusCategoryKey);
    }

    @Override
    public boolean isInProgressStatus(String statusCategoryKey) {
        return STATUS_IN_PROGRESS.equals(statusCategoryKey);
    }

    @Override
    public boolean isHighPriority(String priorityName) {
        return "High".equals(priorityName) || "Highest".equals(priorityName);
    }

    @Override
    public boolean isBugType(String issueType) {
        return "Bug".equals(issueType);
    }
}
