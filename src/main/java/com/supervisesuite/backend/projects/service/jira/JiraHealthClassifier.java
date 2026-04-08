package com.supervisesuite.backend.projects.service.jira;

public interface JiraHealthClassifier {

    boolean isDoneStatus(String statusCategoryKey);

    boolean isToDoStatus(String statusCategoryKey);

    boolean isInProgressStatus(String statusCategoryKey);

    boolean isHighPriority(String priorityName);

    boolean isBugType(String issueType);
}
