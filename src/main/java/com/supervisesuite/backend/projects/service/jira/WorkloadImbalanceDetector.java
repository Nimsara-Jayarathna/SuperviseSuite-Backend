package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import java.util.List;

/**
 * Strategy interface to evaluate team workload distribution and flag critical imbalances.
 */
public interface WorkloadImbalanceDetector {

    /**
     * Examines member workloads and determines if an imbalance exists.
     * 
     * @param members the computed member workload rows
     * @return analysis result containing boolean flag and diagnostic message
     */
    Result detect(List<JiraWorkloadDto.MemberRow> members);

    record Result(boolean detected, String message) {
        public static Result none() {
            return new Result(false, null);
        }
    }
}
