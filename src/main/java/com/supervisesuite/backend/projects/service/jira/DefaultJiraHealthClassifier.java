package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.config.JiraProperties;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class DefaultJiraHealthClassifier implements JiraHealthClassifier {

    /** Jira status category keys — defined by Atlassian, not configurable. */
    private static final String STATUS_TODO = "new";
    private static final String STATUS_IN_PROGRESS = "indeterminate";
    private static final String STATUS_DONE = "done";
    private final Set<String> highPriorityNames;
    private final Set<String> bugTypeNames;

    DefaultJiraHealthClassifier(JiraProperties jiraProperties) {
        JiraProperties.Analytics analytics = jiraProperties.getAnalytics();
        this.highPriorityNames = normalizeNames(analytics == null ? null : analytics.getHighPriorityNames());
        this.bugTypeNames = normalizeNames(analytics == null ? null : analytics.getBugTypeNames());
    }

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
        return highPriorityNames.contains(normalize(priorityName));
    }

    @Override
    public boolean isBugType(String issueType) {
        return bugTypeNames.contains(normalize(issueType));
    }

    private static Set<String> normalizeNames(Iterable<String> names) {
        if (names == null) {
            return Set.of();
        }

        return java.util.stream.StreamSupport.stream(names.spliterator(), false)
                .map(DefaultJiraHealthClassifier::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
