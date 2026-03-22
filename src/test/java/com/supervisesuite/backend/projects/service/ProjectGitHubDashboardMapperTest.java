package com.supervisesuite.backend.projects.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectGitHubDashboardMapperTest {

    private ProjectGitHubDashboardMapper mapper;

    @BeforeEach
    void setUp() {
        GitHubProperties properties = new GitHubProperties();
        properties.setDefaultBranch("main");
        properties.setDashboardContributorsLimit(5);
        properties.setActivityActiveWindowHours(48);
        mapper = new ProjectGitHubDashboardMapper(properties);
    }

    @Test
    void noRepository_returnsIdleEmptyDashboard() {
        ProjectGitHubDashboardDto dto = mapper.noRepository();

        assertThat(dto.isRepositoryLinked()).isFalse();
        assertThat(dto.getRepository()).isNull();
        assertThat(dto.getActivitySummary().getTotalCommits()).isEqualTo(0);
        assertThat(dto.getContributors()).isEmpty();
        assertThat(dto.getRecentCommits()).isEmpty();
    }

    @Test
    void toDashboard_mapsCommitsAndContributorCounts() {
        Instant now = Instant.now();
        List<ProjectCommitDto> commits = List.of(
            new ProjectCommitDto("1", "feat: add", "Alice", now.minusSeconds(10)),
            new ProjectCommitDto("2", "fix: bug", "Bob", now.minusSeconds(20)),
            new ProjectCommitDto("3", "chore", "Alice", now.minusSeconds(30))
        );

        ProjectGitHubDashboardDto dto = mapper.toDashboard(
            "https://github.com/acme/repo",
            null,
            commits,
            now
        );

        assertThat(dto.isRepositoryLinked()).isTrue();
        assertThat(dto.getRepository().getName()).isEqualTo("repo");
        assertThat(dto.getRepository().getDefaultBranch()).isEqualTo("main");
        assertThat(dto.getActivitySummary().getTotalCommits()).isEqualTo(3);
        assertThat(dto.getContributors()).hasSize(2);
        assertThat(dto.getContributors().get(0).getName()).isEqualTo("Alice");
        assertThat(dto.getContributors().get(0).getCommitCount()).isEqualTo(2);
    }
}
