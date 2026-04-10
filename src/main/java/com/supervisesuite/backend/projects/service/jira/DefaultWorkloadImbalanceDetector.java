package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class DefaultWorkloadImbalanceDetector implements WorkloadImbalanceDetector {

    /** Imbalance threshold: outlier must have at least this many open issues. */
    private static final int IMBALANCE_MIN_OPEN = 3;

    /** Imbalance threshold: outlier must have strictly more than this multiple of the minimum. */
    private static final int IMBALANCE_MULTIPLIER = 3;

    @Override
    public Result detect(List<JiraWorkloadDto.MemberRow> members) {
        if (members.size() < 2) {
            return Result.none();
        }

        int maxOpen = members.stream()
                .mapToInt(JiraWorkloadDto.MemberRow::openIssues)
                .max()
                .orElse(0);
                
        int minOpen = members.stream()
                .mapToInt(JiraWorkloadDto.MemberRow::openIssues)
                .min()
                .orElse(0);

        if (maxOpen < IMBALANCE_MIN_OPEN || maxOpen <= IMBALANCE_MULTIPLIER * minOpen) {
            return Result.none();
        }

        JiraWorkloadDto.MemberRow outlier = members.stream()
                .max(Comparator.comparingInt(JiraWorkloadDto.MemberRow::openIssues))
                .orElseThrow();
                
        JiraWorkloadDto.MemberRow leastBurdened = members.stream()
                .min(Comparator.comparingInt(JiraWorkloadDto.MemberRow::openIssues))
                .orElseThrow();

        String message = "%s has %dx more open issues than %s".formatted(
                outlier.displayName(),
                IMBALANCE_MULTIPLIER,
                leastBurdened.displayName());

        return new Result(true, message);
    }
}
