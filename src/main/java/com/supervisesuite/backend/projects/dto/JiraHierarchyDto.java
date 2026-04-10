package com.supervisesuite.backend.projects.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class JiraHierarchyDto {

    private List<JiraHierarchyNodeDto> roots;
    private List<JiraHierarchyNodeDto> orphans;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class JiraHierarchyNodeDto {
        private String issueKey;
        private String summary;
        private String issueType;
        private String status;
        private String priority;
        private String assigneeDisplayName;
        private Integer storyPoints;
        private List<JiraHierarchyNodeDto> children;
    }
}
