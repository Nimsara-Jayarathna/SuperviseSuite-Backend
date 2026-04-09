package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.config.JiraProperties;
import org.junit.jupiter.api.Test;

class DefaultJiraHealthClassifierTest {

    private final DefaultJiraHealthClassifier classifier =
            new DefaultJiraHealthClassifier(new JiraProperties());

    @Test
    void isDoneStatus_matchesDoneKeyOnly() {
        assertThat(classifier.isDoneStatus("done")).isTrue();
        assertThat(classifier.isDoneStatus("new")).isFalse();
        assertThat(classifier.isDoneStatus("indeterminate")).isFalse();
    }

    @Test
    void isToDoAndInProgressStatus_matchExpectedKeys() {
        assertThat(classifier.isToDoStatus("new")).isTrue();
        assertThat(classifier.isToDoStatus("done")).isFalse();

        assertThat(classifier.isInProgressStatus("indeterminate")).isTrue();
        assertThat(classifier.isInProgressStatus("new")).isFalse();
    }

    @Test
    void isHighPriority_andIsBugType_followConfiguredCaseInsensitiveNames() {
        assertThat(classifier.isHighPriority("High")).isTrue();
        assertThat(classifier.isHighPriority("Highest")).isTrue();
        assertThat(classifier.isHighPriority("HIGH")).isTrue();

        assertThat(classifier.isBugType("Bug")).isTrue();
        assertThat(classifier.isBugType("bug")).isTrue();
    }
}
