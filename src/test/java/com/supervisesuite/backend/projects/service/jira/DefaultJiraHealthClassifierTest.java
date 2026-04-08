package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultJiraHealthClassifierTest {

    private final DefaultJiraHealthClassifier classifier = new DefaultJiraHealthClassifier();

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
    void isHighPriority_andIsBugType_areCaseSensitiveByCurrentContract() {
        assertThat(classifier.isHighPriority("High")).isTrue();
        assertThat(classifier.isHighPriority("Highest")).isTrue();
        assertThat(classifier.isHighPriority("HIGH")).isFalse();

        assertThat(classifier.isBugType("Bug")).isTrue();
        assertThat(classifier.isBugType("bug")).isFalse();
    }
}
