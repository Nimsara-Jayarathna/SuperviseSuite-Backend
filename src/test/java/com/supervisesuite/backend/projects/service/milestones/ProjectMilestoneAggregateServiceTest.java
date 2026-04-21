package com.supervisesuite.backend.projects.service.milestones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectMilestone;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectMilestoneAggregateServiceTest {

    @Test
    void computeAndApplyTo_delegatesToPolicyEngineForProgressAndMilestoneDate() {
        ProjectMilestoneRepository repository = mock(ProjectMilestoneRepository.class);
        MilestonePolicyEngine policyEngine = new MilestonePolicyEngine();
        ProjectMilestoneAggregateService service = new ProjectMilestoneAggregateService(repository, policyEngine);

        LocalDate earliestOpen = LocalDate.now().plusDays(2);
        List<ProjectMilestone> milestones = List.of(
                milestone(1, LocalDate.now().plusDays(7), MilestonePolicyEngine.STATUS_PLANNED),
                milestone(2, earliestOpen, MilestonePolicyEngine.STATUS_IN_PROGRESS),
                milestone(3, LocalDate.now().plusDays(10), MilestonePolicyEngine.STATUS_CANCELLED),
                milestone(4, LocalDate.now().minusDays(1), MilestonePolicyEngine.STATUS_COMPLETED));

        ProjectMilestoneAggregateService.ProjectMilestoneAggregates aggregates = service.compute(milestones);
        assertThat(aggregates.milestoneDate()).isEqualTo(earliestOpen);
        assertThat(aggregates.progressPercent()).isEqualTo(33);

        Project project = new Project();
        service.applyTo(project, milestones);
        assertThat(project.getMilestoneDate()).isEqualTo(earliestOpen);
        assertThat(project.getProgressPercent()).isEqualTo(33);
    }

    private static ProjectMilestone milestone(int sequenceNo, LocalDate dueDate, String status) {
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setId(UUID.randomUUID());
        milestone.setProjectId(UUID.randomUUID());
        milestone.setSequenceNo(sequenceNo);
        milestone.setDueDate(dueDate);
        milestone.setStatus(status);
        milestone.setTitle("M" + sequenceNo);
        return milestone;
    }
}
