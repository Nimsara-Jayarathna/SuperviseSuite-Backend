package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JiraSprintProgressServiceImpl implements JiraSprintProgressService {

    private final ProjectJiraIssueRepository jiraIssueRepository;
    private final JiraHealthClassifier jiraHealthClassifier;
    private final JiraProperties jiraProperties;

    JiraSprintProgressServiceImpl(
            ProjectJiraIssueRepository jiraIssueRepository,
            JiraHealthClassifier jiraHealthClassifier,
            JiraProperties jiraProperties) {
        this.jiraIssueRepository = jiraIssueRepository;
        this.jiraHealthClassifier = jiraHealthClassifier;
        this.jiraProperties = jiraProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public JiraSprintProgressDto getSprintProgress(UUID projectId) {
        List<ProjectJiraIssue> issues = jiraIssueRepository.findAllByProjectId(projectId);

        Map<Long, SprintAccumulator> sprintAccumulators = new HashMap<>();
        Map<Instant, VelocityAccumulator> velocityByWeek = new HashMap<>();

        for (ProjectJiraIssue issue : issues) {
            accumulateSprint(issue, sprintAccumulators);
            accumulateVelocity(issue, velocityByWeek);
        }

        List<JiraSprintProgressDto.SprintSummary> sprintSummaries = sprintAccumulators.values().stream()
                .map(SprintAccumulator::toSummary)
                .sorted(sprintRecencyComparator())
                .toList();

        JiraSprintProgressDto.SprintSummary activeSprint = sprintSummaries.stream()
                .filter(summary -> "active".equalsIgnoreCase(summary.sprintState()))
                .findFirst()
                .orElse(null);

        List<JiraSprintProgressDto.SprintSummary> recentSprints = sprintSummaries.stream()
            .limit(resolveRecentSprintsLimit())
                .toList();

        List<JiraSprintProgressDto.VelocityWeek> velocityWeeks = velocityByWeek.entrySet().stream()
                .map(entry -> new JiraSprintProgressDto.VelocityWeek(
                        entry.getKey(),
                    entry.getValue().created,
                    entry.getValue().resolved,
                    entry.getValue().averageCycleDays()))
                .sorted(Comparator.comparing(JiraSprintProgressDto.VelocityWeek::weekStart))
                .toList();

        return new JiraSprintProgressDto(
                activeSprint,
                recentSprints,
                velocityWeeks,
                isBacklogGrowing(velocityWeeks),
                !sprintSummaries.isEmpty());
    }

    private void accumulateSprint(ProjectJiraIssue issue, Map<Long, SprintAccumulator> sprintAccumulators) {
        Long sprintId = issue.getSprintId();
        if (sprintId == null) {
            return;
        }

        SprintAccumulator accumulator = sprintAccumulators.computeIfAbsent(
                sprintId,
                id -> new SprintAccumulator(
                        id,
                        issue.getSprintName(),
                        issue.getSprintState(),
                        issue.getSprintStartDate(),
                        issue.getSprintEndDate()));

        accumulator.issuesTotal += 1;
        if (jiraHealthClassifier.isDoneStatus(issue.getStatusCategoryKey())) {
            accumulator.issuesDone += 1;
        }

        if (isIncludedAtSprintStart(issue)) {
            accumulator.sprintStartIssueCount += 1;
        }

        BigDecimal storyPoints = issue.getStoryPoints();
        if (storyPoints != null) {
            accumulator.storyPointsAvailable = true;
            accumulator.sprintPointsTotal += storyPoints.doubleValue();
            if (jiraHealthClassifier.isDoneStatus(issue.getStatusCategoryKey())) {
                accumulator.sprintPointsDone += storyPoints.doubleValue();
            }
        }

        accumulator.sprintName = firstNonBlank(accumulator.sprintName, issue.getSprintName());
        accumulator.sprintState = firstNonBlank(accumulator.sprintState, issue.getSprintState());
        accumulator.startDate = mostRecent(accumulator.startDate, issue.getSprintStartDate());
        accumulator.endDate = mostRecent(accumulator.endDate, issue.getSprintEndDate());
    }

    private void accumulateVelocity(ProjectJiraIssue issue, Map<Instant, VelocityAccumulator> velocityByWeek) {
        if (issue.getJiraCreatedAt() != null) {
            Instant weekStart = toWeekStart(issue.getJiraCreatedAt());
            velocityByWeek.computeIfAbsent(weekStart, ignored -> new VelocityAccumulator()).created += 1;
        }

        if (issue.getResolutionDate() != null) {
            Instant weekStart = toWeekStart(issue.getResolutionDate());
            VelocityAccumulator accumulator = velocityByWeek.computeIfAbsent(
                    weekStart,
                    ignored -> new VelocityAccumulator());
            accumulator.resolved += 1;
            if (issue.getJiraCreatedAt() != null && !issue.getResolutionDate().isBefore(issue.getJiraCreatedAt())) {
                double cycleDays = (issue.getResolutionDate().toEpochMilli() - issue.getJiraCreatedAt().toEpochMilli())
                        / 86_400_000.0;
                accumulator.resolvedCycleDaysTotal += cycleDays;
                accumulator.resolvedWithCycleData += 1;
            }
        }
    }

    private static boolean isIncludedAtSprintStart(ProjectJiraIssue issue) {
        if (issue.getSprintStartDate() == null || issue.getJiraCreatedAt() == null) {
            return false;
        }
        return !issue.getJiraCreatedAt().isAfter(issue.getSprintStartDate());
    }

    private Comparator<JiraSprintProgressDto.SprintSummary> sprintRecencyComparator() {
        return Comparator
                .comparing(JiraSprintProgressDto.SprintSummary::endDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(JiraSprintProgressDto.SprintSummary::startDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(JiraSprintProgressDto.SprintSummary::sprintId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private boolean isBacklogGrowing(List<JiraSprintProgressDto.VelocityWeek> velocityWeeks) {
        int consecutive = 0;
        int requiredConsecutiveWeeks = resolveBacklogGrowingConsecutiveWeeks();
        for (JiraSprintProgressDto.VelocityWeek week : velocityWeeks) {
            if (week.created() > week.resolved()) {
                consecutive += 1;
                if (consecutive >= requiredConsecutiveWeeks) {
                    return true;
                }
            } else {
                consecutive = 0;
            }
        }
        return false;
    }

    private int resolveRecentSprintsLimit() {
        JiraProperties.Analytics analytics = jiraProperties.getAnalytics();
        if (analytics == null || analytics.getRecentSprintsLimit() <= 0) {
            return 3;
        }
        return analytics.getRecentSprintsLimit();
    }

    private int resolveBacklogGrowingConsecutiveWeeks() {
        JiraProperties.Analytics analytics = jiraProperties.getAnalytics();
        if (analytics == null || analytics.getBacklogGrowingConsecutiveWeeks() <= 0) {
            return 2;
        }
        return analytics.getBacklogGrowingConsecutiveWeeks();
    }

    private static Instant toWeekStart(Instant value) {
        LocalDate date = value.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant mostRecent(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isAfter(current)) {
            return candidate;
        }
        return current;
    }

    private static String firstNonBlank(String current, String candidate) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        if (candidate == null || candidate.isBlank()) {
            return current;
        }
        return candidate;
    }

    private static final class SprintAccumulator {
        private final Long sprintId;
        private String sprintName;
        private String sprintState;
        private Instant startDate;
        private Instant endDate;
        private int sprintStartIssueCount;
        private int issuesDone;
        private int issuesTotal;
        private double sprintPointsDone;
        private double sprintPointsTotal;
        private boolean storyPointsAvailable;

        private SprintAccumulator(Long sprintId, String sprintName, String sprintState, Instant startDate, Instant endDate) {
            this.sprintId = sprintId;
            this.sprintName = sprintName;
            this.sprintState = sprintState;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        private JiraSprintProgressDto.SprintSummary toSummary() {
            double completionPercent = issuesTotal == 0 ? 0.0 : ((double) issuesDone / issuesTotal) * 100.0;
            return new JiraSprintProgressDto.SprintSummary(
                    sprintId,
                    sprintName,
                    sprintState,
                    startDate,
                    endDate,
                    sprintStartIssueCount,
                    completionPercent,
                    issuesDone,
                    issuesTotal,
                    sprintPointsDone,
                    sprintPointsTotal,
                    storyPointsAvailable);
        }
    }

    private static final class VelocityAccumulator {
        private long created;
        private long resolved;
        private double resolvedCycleDaysTotal;
        private long resolvedWithCycleData;

        private Double averageCycleDays() {
            if (resolvedWithCycleData == 0) {
                return null;
            }
            return resolvedCycleDaysTotal / resolvedWithCycleData;
        }
    }
}
