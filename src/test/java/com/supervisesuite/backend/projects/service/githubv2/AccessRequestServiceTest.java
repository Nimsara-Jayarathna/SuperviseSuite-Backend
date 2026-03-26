package com.supervisesuite.backend.projects.service.githubv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.FrontendProperties;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedSummaryDto;
import com.supervisesuite.backend.projects.entity.GitHubAccessRequestV2;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import com.supervisesuite.backend.projects.repository.GitHubAccessRequestV2Repository;
import com.supervisesuite.backend.projects.repository.GitHubAccessSourceRepository;
import com.supervisesuite.backend.projects.repository.GitHubRepositoryEntityRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessRequestServiceTest {

    @Mock
    private GitHubAccessRequestV2Repository accessRequestRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GitHubAccessSourceRepository accessSourceRepository;

    @Mock
    private GitHubRepositoryEntityRepository repositoryRepository;

    @Mock
    private GitHubAppAuthService gitHubAppAuthService;

    @Mock
    private GitHubIntegrationGuardService guardService;

    private AccessRequestService accessRequestService;
    private GitHubProperties gitHubProperties;
    private FrontendProperties frontendProperties;

    @BeforeEach
    void setUp() {
        gitHubProperties = new GitHubProperties();
        frontendProperties = new FrontendProperties();
        accessRequestService = new AccessRequestService(
            accessRequestRepository,
            projectRepository,
            accessSourceRepository,
            repositoryRepository,
            gitHubAppAuthService,
            guardService,
            gitHubProperties,
            frontendProperties
        );
    }

    @Test
    void getSummary_returnsValidSummary() {
        String resultToken = "valid-token";
        UUID projectId = UUID.randomUUID();
        Long installationId = 123L;

        GitHubAccessRequestV2 request = new GitHubAccessRequestV2();
        request.setProjectId(projectId);
        request.setInstallationId(installationId);
        request.setUsedAt(Instant.now());
        request.setResultExpiresAt(Instant.now().plusSeconds(600));

        when(accessRequestRepository.findByResultTokenHash(any())).thenReturn(Optional.of(request));
        
        Project project = new Project();
        project.setId(projectId);
        project.setName("Test Project");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        GitHubAppAuthService.GitHubInstallationRepositoryContext repo =
            new GitHubAppAuthService.GitHubInstallationRepositoryContext(1L, "repo", "owner/repo", "owner", "url", "main");
        
        when(gitHubAppAuthService.fetchInstallationRepositories(eq(installationId), anyInt(), anyInt()))
            .thenReturn(new GitHubAppAuthService.GitHubInstallationRepositoriesPageContext(List.of(repo), 1L));

        GitHubAccessUpdatedSummaryDto summary = accessRequestService.getSummary(resultToken);

        assertThat(summary.getProjectTitle()).isEqualTo("Test Project");
        assertThat(summary.getInstallationId()).isEqualTo(installationId);
        assertThat(summary.getRepositories()).hasSize(1);
    }

    @Test
    void acknowledge_updatesAcknowledgeFlag() {
        String resultToken = "valid-token";
        GitHubAccessRequestV2 request = new GitHubAccessRequestV2();
        request.setProjectId(UUID.randomUUID());

        when(accessRequestRepository.findByResultTokenHash(any())).thenReturn(Optional.of(request));

        accessRequestService.acknowledge(resultToken);

        assertThat(request.getResultAcknowledgedAt()).isNotNull();
        verify(accessRequestRepository).save(request);
    }
}
