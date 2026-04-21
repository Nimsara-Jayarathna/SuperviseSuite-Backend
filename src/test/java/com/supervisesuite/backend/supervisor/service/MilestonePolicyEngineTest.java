package com.supervisesuite.backend.supervisor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.service.milestones.MilestonePolicyEngine;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MilestonePolicyEngineTest {

    private final MilestonePolicyEngine policy = new MilestonePolicyEngine();

    @Test
    void validateDueDateForStatus_openMilestonePastDate_throwsValidationException() {
        assertThatThrownBy(() -> policy.validateDueDateForStatus(
                LocalDate.now().minusDays(1),
                MilestonePolicyEngine.STATUS_PLANNED,
                LocalDate.now()))
                .isInstanceOfSatisfying(ValidationException.class, exception -> {
                    assertThat(exception.getDetails()).hasSize(1);
                    assertThat(exception.getDetails().getFirst().getField()).isEqualTo("dueDate");
                });
    }

    @Test
    void validateDueDateForStatus_terminalMilestonePastDate_allowed() {
        policy.validateDueDateForStatus(
                LocalDate.now().minusDays(1),
                MilestonePolicyEngine.STATUS_COMPLETED,
                LocalDate.now());
    }

    @Test
    void validateChronologyWithPrevious_whenCurrentBeforePrevious_throwsValidationException() {
        assertThatThrownBy(() -> policy.validateChronologyWithPrevious(
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(5)))
                .isInstanceOfSatisfying(ValidationException.class, exception -> {
                    assertThat(exception.getDetails()).hasSize(1);
                    assertThat(exception.getDetails().getFirst().getField()).isEqualTo("sequenceNo");
                });
    }

    @Test
    void validateStatusTransition_completedToPlanned_throwsValidationException() {
        assertThatThrownBy(() -> policy.validateStatusTransition(
                MilestonePolicyEngine.STATUS_COMPLETED,
                MilestonePolicyEngine.STATUS_PLANNED))
                .isInstanceOfSatisfying(ValidationException.class, exception -> {
                    assertThat(exception.getDetails()).hasSize(1);
                    assertThat(exception.getDetails().getFirst().getField()).isEqualTo("status");
                });
    }

    @Test
    void validateChronologyForUpdate_whenNewDateAfterNext_throwsValidationException() {
        ProjectMilestone first = milestone(1, LocalDate.now().plusDays(1), MilestonePolicyEngine.STATUS_PLANNED);
        ProjectMilestone second = milestone(2, LocalDate.now().plusDays(2), MilestonePolicyEngine.STATUS_PLANNED);
        ProjectMilestone third = milestone(3, LocalDate.now().plusDays(3), MilestonePolicyEngine.STATUS_PLANNED);

        assertThatThrownBy(() -> policy.validateChronologyForUpdate(
                List.of(first, second, third),
                second.getId(),
                LocalDate.now().plusDays(5)))
                .isInstanceOfSatisfying(ValidationException.class, exception -> {
                    assertThat(exception.getDetails()).hasSize(1);
                    assertThat(exception.getDetails().getFirst().getField()).isEqualTo("sequenceNo");
                });
    }

    @Test
    void computeInsights_detectsOverdueAndChronologyRisk() {
        ProjectMilestone first = milestone(1, LocalDate.now().plusDays(1), MilestonePolicyEngine.STATUS_PLANNED);
        ProjectMilestone second = milestone(2, LocalDate.now().minusDays(1), MilestonePolicyEngine.STATUS_PLANNED);

        MilestonePolicyEngine.MilestoneInsightsSnapshot snapshot = policy.computeInsights(
                List.of(first, second),
                LocalDate.now());

        assertThat(snapshot.overdueOpenMilestones()).isEqualTo(1);
        assertThat(snapshot.dueSoonCount()).isEqualTo(1);
        assertThat(snapshot.timelineRiskLevel()).isEqualTo(MilestonePolicyEngine.RISK_HIGH);
        MilestonePolicyEngine.MilestoneSignal signal = snapshot.signalsByMilestoneId().get(second.getId());
        assertThat(signal).isNotNull();
        assertThat(signal.isOverdue()).isTrue();
        assertThat(signal.isChronologyViolation()).isTrue();
    }

    @Test
    void computeProjectMilestoneDate_ignoresTerminalMilestones() {
        LocalDate completedDue = LocalDate.now().minusDays(2);
        LocalDate plannedDue = LocalDate.now().plusDays(10);
        ProjectMilestone completed =
                milestone(1, completedDue, MilestonePolicyEngine.STATUS_COMPLETED);
        ProjectMilestone planned =
                milestone(2, plannedDue, MilestonePolicyEngine.STATUS_PLANNED);

        LocalDate milestoneDate = policy.computeProjectMilestoneDate(List.of(completed, planned));

        assertThat(milestoneDate).isEqualTo(plannedDue);
    }

    @Test
    void computeProjectMilestoneDate_allTerminal_returnsNull() {
        ProjectMilestone completed =
                milestone(1, LocalDate.now().minusDays(4), MilestonePolicyEngine.STATUS_COMPLETED);
        ProjectMilestone missed =
                milestone(2, LocalDate.now().minusDays(1), MilestonePolicyEngine.STATUS_MISSED);

        LocalDate milestoneDate = policy.computeProjectMilestoneDate(List.of(completed, missed));

        assertThat(milestoneDate).isNull();
    }

    private static ProjectMilestone milestone(int sequenceNo, LocalDate dueDate, String status) {
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setId(UUID.randomUUID());
        milestone.setProjectId(UUID.randomUUID());
        milestone.setSequenceNo(sequenceNo);
        milestone.setDueDate(dueDate);
        milestone.setStatus(status);
        return milestone;
    }
}
