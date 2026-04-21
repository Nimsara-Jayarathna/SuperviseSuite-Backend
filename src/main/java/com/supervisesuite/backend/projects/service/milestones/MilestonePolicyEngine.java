package com.supervisesuite.backend.projects.service.milestones;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class MilestonePolicyEngine {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_MISSED = "MISSED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            STATUS_PLANNED,
            STATUS_IN_PROGRESS,
            STATUS_COMPLETED,
            STATUS_MISSED,
            STATUS_CANCELLED);
    private static final Set<String> OPEN_STATUSES = Set.of(STATUS_PLANNED, STATUS_IN_PROGRESS);
    private static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_COMPLETED, STATUS_MISSED, STATUS_CANCELLED);

    public String normalizeAndValidateStatus(String rawStatus) {
        String normalizedStatus = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new ValidationException("status", "Milestone status is invalid.");
        }
        return normalizedStatus;
    }

    public void validateDueDateForStatus(LocalDate dueDate, String status, LocalDate today) {
        if (dueDate == null || status == null || today == null) {
            return;
        }
        if (OPEN_STATUSES.contains(status) && dueDate.isBefore(today)) {
            throw new ValidationException(
                    "dueDate",
                    "Open milestones must use today or a future due date.");
        }
    }

    public void validateStatusTransition(String currentStatus, String nextStatus) {
        if (currentStatus == null || nextStatus == null || currentStatus.equals(nextStatus)) {
            return;
        }

        if (STATUS_COMPLETED.equals(currentStatus)) {
            throw new ValidationException(
                    "status",
                    "Completed milestones cannot be moved to another status.");
        }

        if (TERMINAL_STATUSES.contains(currentStatus) && OPEN_STATUSES.contains(nextStatus)) {
            throw new ValidationException(
                    "status",
                    "Terminal milestones cannot move back to open states.");
        }
    }

    public void validateChronologyWithPrevious(LocalDate previousDueDate, LocalDate currentDueDate) {
        if (previousDueDate == null || currentDueDate == null) {
            return;
        }
        if (currentDueDate.isBefore(previousDueDate)) {
            throw new ValidationException(
                    "sequenceNo",
                    "Milestone due date must be on or after the previous milestone due date.");
        }
    }

    public void validateChronologyForUpdate(List<ProjectMilestone> milestones, UUID targetMilestoneId, LocalDate newDueDate) {
        if (milestones == null || targetMilestoneId == null || newDueDate == null) {
            return;
        }
        List<ProjectMilestone> orderedMilestones = milestones.stream()
                .sorted(Comparator.comparing(ProjectMilestone::getSequenceNo))
                .toList();
        int index = -1;
        for (int i = 0; i < orderedMilestones.size(); i++) {
            if (targetMilestoneId.equals(orderedMilestones.get(i).getId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }

        if (index > 0) {
            LocalDate previousDueDate = orderedMilestones.get(index - 1).getDueDate();
            if (previousDueDate != null && newDueDate.isBefore(previousDueDate)) {
                throw new ValidationException(
                        "sequenceNo",
                        "Milestone due date must be on or after the previous milestone due date.");
            }
        }

        if (index < orderedMilestones.size() - 1) {
            LocalDate nextDueDate = orderedMilestones.get(index + 1).getDueDate();
            if (nextDueDate != null && newDueDate.isAfter(nextDueDate)) {
                throw new ValidationException(
                        "sequenceNo",
                        "Milestone due date must be on or before the next milestone due date.");
            }
        }
    }

    public int calculateProgressPercent(List<ProjectMilestone> milestones) {
        if (milestones == null || milestones.isEmpty()) {
            return 0;
        }

        long activeMilestones = milestones.stream()
                .filter(milestone -> !STATUS_CANCELLED.equals(milestone.getStatus()))
                .count();
        if (activeMilestones == 0) {
            return 0;
        }

        long completedMilestones = milestones.stream()
                .filter(milestone -> !STATUS_CANCELLED.equals(milestone.getStatus()))
                .filter(milestone -> STATUS_COMPLETED.equals(milestone.getStatus()))
                .count();
        return (int) Math.round((completedMilestones * 100.0) / activeMilestones);
    }

    public LocalDate computeProjectMilestoneDate(List<ProjectMilestone> milestones) {
        if (milestones == null || milestones.isEmpty()) {
            return null;
        }
        return milestones.stream()
                .filter(milestone -> OPEN_STATUSES.contains(milestone.getStatus()))
                .map(ProjectMilestone::getDueDate)
                .filter(dueDate -> dueDate != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    public MilestoneInsightsSnapshot computeInsights(List<ProjectMilestone> milestones, LocalDate today) {
        if (milestones == null || milestones.isEmpty()) {
            return new MilestoneInsightsSnapshot(Map.of(), 0, 0, RISK_LOW);
        }

        List<ProjectMilestone> orderedMilestones = milestones.stream()
                .sorted(Comparator.comparing(ProjectMilestone::getSequenceNo))
                .toList();

        Map<UUID, MilestoneSignal> signalsByMilestoneId = new HashMap<>();
        int overdueOpenMilestones = 0;
        int dueSoonCount = 0;
        int chronologyViolationCount = 0;
        LocalDate dueSoonBoundary = today.plusDays(7);

        LocalDate previousDueDate = null;
        for (ProjectMilestone milestone : orderedMilestones) {
            LocalDate dueDate = milestone.getDueDate();
            boolean isOpenMilestone = OPEN_STATUSES.contains(milestone.getStatus());
            boolean isOverdue = false;
            int daysOverdue = 0;
            if (isOpenMilestone && dueDate != null && dueDate.isBefore(today)) {
                isOverdue = true;
                daysOverdue = (int) ChronoUnit.DAYS.between(dueDate, today);
                overdueOpenMilestones++;
            }
            if (isOpenMilestone
                    && dueDate != null
                    && !dueDate.isBefore(today)
                    && !dueDate.isAfter(dueSoonBoundary)) {
                dueSoonCount++;
            }
            boolean chronologyViolation = previousDueDate != null
                    && dueDate != null
                    && dueDate.isBefore(previousDueDate);
            if (chronologyViolation) {
                chronologyViolationCount++;
            }
            previousDueDate = dueDate;

            signalsByMilestoneId.put(
                    milestone.getId(),
                    new MilestoneSignal(isOverdue, daysOverdue, chronologyViolation));
        }

        String timelineRiskLevel = RISK_LOW;
        if (chronologyViolationCount > 0 || overdueOpenMilestones >= 2) {
            timelineRiskLevel = RISK_HIGH;
        } else if (overdueOpenMilestones == 1 || dueSoonCount >= 2) {
            timelineRiskLevel = RISK_MEDIUM;
        }

        return new MilestoneInsightsSnapshot(
                Map.copyOf(signalsByMilestoneId),
                overdueOpenMilestones,
                dueSoonCount,
                timelineRiskLevel);
    }

    public record MilestoneSignal(boolean isOverdue, int daysOverdue, boolean isChronologyViolation) {
    }

    public record MilestoneInsightsSnapshot(
            Map<UUID, MilestoneSignal> signalsByMilestoneId,
            int overdueOpenMilestones,
            int dueSoonCount,
            String timelineRiskLevel) {
    }
}
