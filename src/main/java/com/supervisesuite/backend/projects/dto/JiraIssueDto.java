package com.supervisesuite.backend.projects.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        /** Sprint metadata — Jira commonly stores this in customfield_10020. */
        @JsonProperty("customfield_10020")
        private List<Sprint> sprints;

        /** Alternative sprint custom field IDs seen in some Jira workspaces. */
        @JsonProperty("customfield_10021")
        private Object sprintField10021;

        @JsonProperty("customfield_10026")
        private Object sprintField10026;

        private final Map<String, Object> additionalCustomFields = new LinkedHashMap<>();

        @JsonProperty("duedate")
        private String dueDate;

        @JsonProperty("resolutiondate")
        private String resolutionDate;

        private String created;

        private String updated;

        private Parent parent;

        @JsonAnySetter
        public void captureAdditionalField(String fieldName, Object value) {
            if (fieldName != null && fieldName.startsWith("customfield_")) {
                additionalCustomFields.put(fieldName, value);
            }
        }

        public List<Sprint> getSprints() {
            if (sprints != null && !sprints.isEmpty()) {
                return sprints;
            }

            List<Sprint> from10021 = parseSprintsFromRawField(sprintField10021);
            if (!from10021.isEmpty()) {
                return from10021;
            }

            List<Sprint> from10026 = parseSprintsFromRawField(sprintField10026);
            if (!from10026.isEmpty()) {
                return from10026;
            }

            for (Map.Entry<String, Object> entry : additionalCustomFields.entrySet()) {
                String fieldName = entry.getKey();
                if ("customfield_10020".equals(fieldName)
                        || "customfield_10021".equals(fieldName)
                        || "customfield_10026".equals(fieldName)) {
                    continue;
                }

                List<Sprint> fromAnyCustomField = parseSprintsFromRawField(entry.getValue());
                if (!fromAnyCustomField.isEmpty()) {
                    return fromAnyCustomField;
                }
            }

            return List.of();
        }

        private static List<Sprint> parseSprintsFromRawField(Object raw) {
            if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
                return List.of();
            }

            List<Sprint> parsed = new ArrayList<>();
            for (Object item : rawList) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }

                Sprint sprint = new Sprint();
                sprint.setId(toLong(map.get("id")));
                sprint.setName(toStringValue(map.get("name")));
                sprint.setState(toStringValue(map.get("state")));
                sprint.setStartDate(toStringValue(map.get("startDate")));
                sprint.setEndDate(toStringValue(map.get("endDate")));

                if (sprint.getId() != null || sprint.getName() != null) {
                    parsed.add(sprint);
                }
            }

            return parsed;
        }

        private static String toStringValue(Object value) {
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : text;
        }

        private static Long toLong(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sprint {

        private Long id;

        private String name;

        private String state;

        @JsonProperty("startDate")
        private String startDate;

        @JsonProperty("endDate")
        private String endDate;
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
