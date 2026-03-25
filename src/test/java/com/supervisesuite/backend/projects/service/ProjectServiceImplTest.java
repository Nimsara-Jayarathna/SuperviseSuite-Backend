package com.supervisesuite.backend.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.error.DomainException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.entity.GitHubAppInstallation;
import com.supervisesuite.backend.projects.entity.ProjectGitHubInstallationAuthorization;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import com.supervisesuite.backend.projects.integration.github.GitHubCommitClient;
import com.supervisesuite.backend.projects.repository.GitHubAppInstallationRepository;
import com.supervisesuite.backend.projects.repository.ProjectGitHubInstallationAuthorizationRepository;
import com.supervisesuite.backend.projects.service.githubv2.GitHubSyncService;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkCommitRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkContributorRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private com.supervisesuite.backend.projects.repository.ProjectRepository projectRepository;

    @Mock
    private GitHubCommitClient gitHubCommitClient;

    @Mock
    private ProjectGitHubDashboardMapper dashboardMapper;

    @Mock
    private GitHubAppAuthService gitHubAppAuthService;

    @Mock
    private GitHubAppInstallationRepository gitHubAppInstallationRepository;

    @Mock
    private ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository;

    @Mock
    private ProjectRepositoryLinkRepository projectRepositoryLinkRepository;

    @Mock
    private ProjectRepositoryLinkCommitRepository projectRepositoryLinkCommitRepository;

    @Mock
    private ProjectRepositoryLinkContributorRepository projectRepositoryLinkContributorRepository;

    @Mock
    private RepositoryLinkService repositoryLinkService;

    @Mock
    private GitHubSyncService gitHubSyncService;

    private ProjectServiceImpl projectService;
    private GitHubProperties gitHubProperties;

    private UUID projectId;
    private UUID supervisorUserId;

    @BeforeEach
    void setUp() {
        gitHubProperties = new GitHubProperties();
        gitHubProperties.setDefaultBranch("main");
        gitHubProperties.setDefaultPageSize(10);
        gitHubProperties.setMaxPageSize(100);

        GitHubProperties.InstallationRepositories installationRepositories = new GitHubProperties.InstallationRepositories();
        installationRepositories.setDefaultPageSize(25);
        installationRepositories.setMaxPageSize(50);
        gitHubProperties.setInstallationRepositories(installationRepositories);

        projectService = new ProjectServiceImpl(
            gitHubCommitClient,
            dashboardMapper,
            gitHubAppAuthService,
            gitHubAppInstallationRepository,
            projectGitHubInstallationAuthorizationRepository,
            projectRepository,
            projectRepositoryLinkRepository,
            projectRepositoryLinkCommitRepository,
            projectRepositoryLinkContributorRepository,
            repositoryLinkService,
            gitHubSyncService,
            gitHubProperties
        );

        projectId = UUID.randomUUID();
        supervisorUserId = UUID.randomUUID();
    }

    @Test
    void getInstallationRepositories_mapsPageAndItems() {
        Long installationId = 42L;

        when(gitHubAppInstallationRepository.findByInstallationId(installationId))
            .thenReturn(Optional.of(activeInstallation(installationId)));
        when(projectGitHubInstallationAuthorizationRepository.existsByProjectIdAndInstallationId(projectId, installationId))
            .thenReturn(true);

        GitHubAppAuthService.GitHubInstallationRepositoryContext firstRepository =
            new GitHubAppAuthService.GitHubInstallationRepositoryContext(
                10L,
                "core-api",
                "acme/core-api",
                "acme",
                "https://github.com/acme/core-api",
                "main"
            );
        GitHubAppAuthService.GitHubInstallationRepositoryContext secondRepository =
            new GitHubAppAuthService.GitHubInstallationRepositoryContext(
                11L,
                "ui",
                "acme/ui",
                "acme",
                "https://github.com/acme/ui",
                "develop"
            );

        when(repositoryLinkService.fetchInstallationRepositories(installationId, 1, 2))
            .thenReturn(new GitHubAppAuthService.GitHubInstallationRepositoriesPageContext(
                List.of(firstRepository, secondRepository),
                5L
            ));

        GitHubInstallationRepositoryPageDto result = projectService.getInstallationRepositories(
            projectId,
            installationId,
            supervisorUserId,
            1,
            2
        );

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getReturnedCount()).isEqualTo(2);
        assertThat(result.getTotalCount()).isEqualTo(5L);
        assertThat(result.isHasNext()).isTrue();
        assertThat(result.isHasPrevious()).isFalse();
        assertThat(result.getNextPage()).isEqualTo(2);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getDefaultBranch()).isEqualTo("main");
        assertThat(result.getItems().get(1).getDefaultBranch()).isEqualTo("develop");
    }

    @Test
    void getInstallationRepositories_rejectsUnauthorizedInstallation() {
        Long installationId = 43L;

        when(gitHubAppInstallationRepository.findByInstallationId(installationId))
            .thenReturn(Optional.of(activeInstallation(installationId)));
        when(projectGitHubInstallationAuthorizationRepository.existsByProjectIdAndInstallationId(projectId, installationId))
            .thenReturn(false);

        assertThatThrownBy(() -> projectService.getInstallationRepositories(
            projectId,
            installationId,
            supervisorUserId,
            1,
            10
        ))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void linkProjectToInstallationRepository_persistsSelectionAndRefreshes() {
        Long installationId = 99L;
        Long selectedRepositoryId = 777L;
        String repositoryUrl = "https://github.com/acme/core-api";
        UUID storedRepositoryId = UUID.randomUUID();
        ProjectRepositoryLink link = new ProjectRepositoryLink();
        link.setId(storedRepositoryId);
        link.setRepositoryName("core-api");
        link.setRepositoryUrl(repositoryUrl);
        link.setGithubInstallationId(installationId);
        link.setGithubRepoId(selectedRepositoryId);
        link.setDefaultBranch("main");

        when(repositoryLinkService.linkRepository(projectId, installationId, selectedRepositoryId, supervisorUserId))
            .thenReturn(link);

        ProjectGitHubRepositoryLinkDto result = projectService.linkProjectToInstallationRepository(
            projectId,
            installationId,
            selectedRepositoryId,
            supervisorUserId
        );

        assertThat(result.getProjectId()).isEqualTo(projectId);
        assertThat(result.getInstallationId()).isEqualTo(installationId);
        assertThat(result.getRepositoryId()).isEqualTo(selectedRepositoryId);
        assertThat(result.getUrl()).isEqualTo(repositoryUrl);
        assertThat(result.getFullName()).isEqualTo("core-api");

        verify(gitHubSyncService).syncRepository(storedRepositoryId);
    }

    @Test
    void switchToManualRepository_removesInstallationArtifacts() {
        String manualUrl = "https://github.com/acme/manual-repo";
        UUID targetId = UUID.randomUUID();
        UUID legacyId = UUID.randomUUID();

        ProjectRepositoryLink target = repositoryRow(targetId, projectId, manualUrl, 10L);
        target.setGithubRepoId(100L);
        target.setRepositoryName("core-api");
        target.setDefaultBranch("main");
        target.setLinkedAt(Instant.now());
        target.setLinkedBySupervisorUserId(supervisorUserId);
        target.setLinkedAt(Instant.now());

        ProjectRepositoryLink legacy = repositoryRow(legacyId, projectId, "https://github.com/acme/legacy", 11L);

        projectService.switchToManualRepository(projectId, manualUrl);

        verify(repositoryLinkService).linkRepositoryByUrl(eq(projectId), any(String.class), eq(null));
    }

    @Test
    void clearGitHubLinkage_purgesRepositoriesAndAuthorizations() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        ProjectRepositoryLink first = repositoryRow(firstId, projectId, "https://github.com/acme/first", 20L);
        ProjectRepositoryLink second = repositoryRow(secondId, projectId, "https://github.com/acme/second", 21L);

        when(projectRepositoryLinkRepository.findByProjectIdOrderByCreatedAtAsc(projectId))
            .thenReturn(List.of(first, second));

        projectService.clearGitHubLinkage(projectId);

        verify(repositoryLinkService).disconnectRepository(firstId);
        verify(repositoryLinkService).disconnectRepository(secondId);
        verify(projectGitHubInstallationAuthorizationRepository).deleteByProjectId(projectId);
    }

    private ProjectRepositoryLink repositoryRow(UUID id, UUID projectId, String repositoryUrl, Long installationId) {
        ProjectRepositoryLink link = new ProjectRepositoryLink();
        link.setId(id);
        link.setProjectId(projectId);
        link.setRepositoryUrl(repositoryUrl);
        link.setGithubInstallationId(installationId);
        link.setIsEnabled(true);
        link.setIsPrimary(false);
        link.setCreatedAt(Instant.now());
        link.setUpdatedAt(Instant.now());
        return link;
    }

    private static GitHubAppInstallation activeInstallation(Long installationId) {
        GitHubAppInstallation installation = new GitHubAppInstallation();
        installation.setInstallationId(installationId);
        installation.setStatus("ACTIVE");
        installation.setCreatedAt(Instant.now());
        return installation;
    }

    private static ProjectGitHubInstallationAuthorization authorization(
        UUID projectId,
        Long installationId,
        UUID supervisorUserId
    ) {
        ProjectGitHubInstallationAuthorization authorization = new ProjectGitHubInstallationAuthorization();
        authorization.setProjectId(projectId);
        authorization.setInstallationId(installationId);
        authorization.setAuthorizedBySupervisorUserId(supervisorUserId);
        authorization.setCreatedAt(Instant.now());
        authorization.setAuthorizedAt(Instant.now());
        return authorization;
    }
}
