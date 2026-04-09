package com.supervisesuite.backend.projects.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueDto {

    private String key;
    private Fields fields;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {

        private String summary;

        @JsonProperty("issuetype")
        private IssueType issueType;

        private Status status;

        private Assignee assignee;

        private Priority priority;

        /** Story points — Jira stores these in customfield_10016. */
        @JsonProperty("customfield_10016")
        private Double storyPoints;

        @JsonProperty("customfield_10020")
        private List<SprintField> sprint;

        @JsonProperty("duedate")
        private String dueDate;

        @JsonProperty("resolutiondate")
        private String resolutionDate;

        private String created;

        private String updated;

        private Parent parent;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueType {
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        private String name;
        private StatusCategory statusCategory;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusCategory {
        private String key;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Assignee {
        private String accountId;
        private String displayName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Priority {
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SprintField {
        private Long id;
        private String name;
        private String state;
        private String startDate;
        private String endDate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Parent {
        private String key;
    }
}
