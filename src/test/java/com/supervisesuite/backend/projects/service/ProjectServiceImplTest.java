package com.supervisesuite.backend.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.entity.GitHubAppInstallation;
import com.supervisesuite.backend.projects.entity.ProjectGitHubInstallationAuthorization;
import com.supervisesuite.backend.projects.entity.ProjectRepository;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import com.supervisesuite.backend.projects.integration.github.GitHubCommitClient;
import com.supervisesuite.backend.projects.repository.GitHubAppInstallationRepository;
import com.supervisesuite.backend.projects.repository.ProjectGitHubInstallationAuthorizationRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryCacheRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryCommitRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryContributorRepository;
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
    private ProjectRepositoryCacheRepository projectRepositoryCacheRepository;

    @Mock
    private ProjectRepositoryCommitRepository projectRepositoryCommitRepository;

    @Mock
    private ProjectRepositoryContributorRepository projectRepositoryContributorRepository;

    @Mock
    private GitHubAppAuthService gitHubAppAuthService;

    @Mock
    private GitHubAppInstallationRepository gitHubAppInstallationRepository;

    @Mock
    private ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository;

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
            projectRepositoryCacheRepository,
            projectRepositoryCommitRepository,
            projectRepositoryContributorRepository,
            gitHubAppAuthService,
            gitHubAppInstallationRepository,
            projectGitHubInstallationAuthorizationRepository,
            projectRepository,
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
        when(projectGitHubInstallationAuthorizationRepository.findByProjectIdAndInstallationId(projectId, installationId))
            .thenReturn(Optional.of(authorization(projectId, installationId, supervisorUserId)));

        GitHubAppAuthService.GitHubInstallationRepositoryContext firstRepository =
            new GitHubAppAuthService.GitHubInstallationRepositoryContext(
                10L,
                "core-api",
                "acme/core-api",
                "acme",
                "https://github.com/acme/core-api",
                null
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

        when(gitHubAppAuthService.fetchInstallationRepositories(installationId, 1, 2))
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
        when(projectGitHubInstallationAuthorizationRepository.findByProjectIdAndInstallationId(projectId, installationId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getInstallationRepositories(
            projectId,
            installationId,
            supervisorUserId,
            1,
            10
        ))
            .isInstanceOfSatisfying(ValidationException.class, exception -> {
                assertThat(exception.getDetails()).anyMatch(detail ->
                    "installationId".equals(detail.getField())
                        && detail.getIssue().contains("not authorized")
                );
            });
    }

    @Test
    void linkProjectToInstallationRepository_persistsSelectionAndRefreshes() {
        Long installationId = 99L;
        Long selectedRepositoryId = 777L;
        String repositoryUrl = "https://github.com/acme/core-api";
        UUID storedRepositoryId = UUID.randomUUID();
        AtomicReference<ProjectRepository> storedRepository = new AtomicReference<>();

        when(gitHubAppInstallationRepository.findByInstallationId(installationId))
            .thenReturn(Optional.of(activeInstallation(installationId)));
        when(projectGitHubInstallationAuthorizationRepository.findByProjectIdAndInstallationId(projectId, installationId))
            .thenReturn(Optional.of(authorization(projectId, installationId, supervisorUserId)));
        when(gitHubAppAuthService.fetchInstallationRepositories(installationId, 1, 50))
            .thenReturn(new GitHubAppAuthService.GitHubInstallationRepositoriesPageContext(
                List.of(new GitHubAppAuthService.GitHubInstallationRepositoryContext(
                    selectedRepositoryId,
                    "core-api",
                    "acme/core-api",
                    "acme",
                    repositoryUrl,
                    "main"
                )),
                1L
            ));
        when(projectRepositoryCacheRepository.findByProjectIdAndProviderAndRepositoryUrl(projectId, "github", repositoryUrl))
            .thenAnswer(ignored -> Optional.ofNullable(storedRepository.get()));
        when(projectRepositoryCacheRepository.findByProjectIdOrderByCreatedAtAsc(projectId))
            .thenReturn(List.of());
        when(projectRepositoryCacheRepository.save(any(ProjectRepository.class)))
            .thenAnswer(invocation -> {
                ProjectRepository repository = invocation.getArgument(0);
                if (repository.getId() == null) {
                    repository.setId(storedRepositoryId);
                }
                storedRepository.set(repository);
                return repository;
            });
        when(projectRepositoryCacheRepository.findById(storedRepositoryId))
            .thenAnswer(ignored -> Optional.ofNullable(storedRepository.get()));

        ProjectServiceImpl spyService = spy(projectService);
        doNothing().when(spyService).refreshGitHubData(projectId, repositoryUrl);

        ProjectGitHubRepositoryLinkDto result = spyService.linkProjectToInstallationRepository(
            projectId,
            installationId,
            selectedRepositoryId,
            supervisorUserId
        );

        assertThat(result.getProjectId()).isEqualTo(projectId);
        assertThat(result.getInstallationId()).isEqualTo(installationId);
        assertThat(result.getRepositoryId()).isEqualTo(selectedRepositoryId);
        assertThat(result.getUrl()).isEqualTo(repositoryUrl);
        assertThat(result.getFullName()).isEqualTo("acme/core-api");

        verify(spyService).refreshGitHubData(projectId, repositoryUrl);
        assertThat(storedRepository.get()).isNotNull();
        assertThat(storedRepository.get().getInstallationId()).isEqualTo(installationId);
        assertThat(storedRepository.get().getRepositoryExternalId()).isEqualTo(selectedRepositoryId);
        assertThat(storedRepository.get().getLinkedBySupervisorUserId()).isEqualTo(supervisorUserId);
    }

    @Test
    void switchToManualRepository_removesInstallationArtifacts() {
        String manualUrl = "https://github.com/acme/manual-repo";
        UUID targetId = UUID.randomUUID();
        UUID legacyId = UUID.randomUUID();

        ProjectRepository target = repositoryRow(targetId, projectId, manualUrl, 10L);
        target.setRepositoryExternalId(100L);
        target.setOwnerLogin("acme");
        target.setDefaultBranch("main");
        target.setLinkedBySupervisorUserId(supervisorUserId);
        target.setLinkedAt(Instant.now());

        ProjectRepository legacy = repositoryRow(legacyId, projectId, "https://github.com/acme/legacy", 11L);

        when(projectRepositoryCacheRepository.findByProjectIdAndProviderAndRepositoryUrl(projectId, "github", manualUrl))
            .thenReturn(Optional.of(target));
        when(projectRepositoryCacheRepository.findByProjectIdOrderByCreatedAtAsc(projectId))
            .thenReturn(List.of(target, legacy));
        when(projectRepositoryCacheRepository.save(any(ProjectRepository.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepositoryCacheRepository.findByInstallationId(10L)).thenReturn(List.of());
        when(projectRepositoryCacheRepository.findByInstallationId(11L)).thenReturn(List.of());
        when(gitHubAppInstallationRepository.findByInstallationId(10L)).thenReturn(Optional.of(activeInstallation(10L)));
        when(gitHubAppInstallationRepository.findByInstallationId(11L)).thenReturn(Optional.of(activeInstallation(11L)));

        projectService.switchToManualRepository(projectId, manualUrl);

        assertThat(target.getInstallationId()).isNull();
        assertThat(target.getRepositoryExternalId()).isNull();
        assertThat(target.getOwnerLogin()).isNull();
        assertThat(target.getDefaultBranch()).isNull();
        assertThat(target.getLinkedBySupervisorUserId()).isNull();
        assertThat(target.getLinkedAt()).isNull();

        verify(projectRepositoryCommitRepository).deleteByRepositoryId(legacyId);
        verify(projectRepositoryContributorRepository).deleteByRepositoryId(legacyId);
        verify(projectRepositoryCacheRepository).deleteAll(argThat(repositories -> {
            List<ProjectRepository> list = StreamSupport.stream(repositories.spliterator(), false)
                .map(ProjectRepository.class::cast)
                .toList();
            return list.size() == 1 && list.contains(legacy);
        }));
        verify(projectGitHubInstallationAuthorizationRepository).deleteByProjectId(projectId);
        verify(gitHubAppInstallationRepository).delete(argThat(installation -> installation.getInstallationId().equals(10L)));
        verify(gitHubAppInstallationRepository).delete(argThat(installation -> installation.getInstallationId().equals(11L)));
    }

    @Test
    void clearGitHubLinkage_purgesRepositoriesAndAuthorizations() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        ProjectRepository first = repositoryRow(firstId, projectId, "https://github.com/acme/first", 20L);
        ProjectRepository second = repositoryRow(secondId, projectId, "https://github.com/acme/second", 21L);

        when(projectRepositoryCacheRepository.findByProjectIdOrderByCreatedAtAsc(projectId))
            .thenReturn(List.of(first, second));
        when(projectRepositoryCacheRepository.findByInstallationId(20L)).thenReturn(List.of());
        when(projectRepositoryCacheRepository.findByInstallationId(21L)).thenReturn(List.of());
        when(gitHubAppInstallationRepository.findByInstallationId(20L)).thenReturn(Optional.of(activeInstallation(20L)));
        when(gitHubAppInstallationRepository.findByInstallationId(21L)).thenReturn(Optional.of(activeInstallation(21L)));

        projectService.clearGitHubLinkage(projectId);

        verify(projectRepositoryCommitRepository).deleteByRepositoryId(firstId);
        verify(projectRepositoryCommitRepository).deleteByRepositoryId(secondId);
        verify(projectRepositoryContributorRepository).deleteByRepositoryId(firstId);
        verify(projectRepositoryContributorRepository).deleteByRepositoryId(secondId);
        verify(projectRepositoryCacheRepository).deleteAll(argThat(repositories -> {
            List<ProjectRepository> list = StreamSupport.stream(repositories.spliterator(), false)
                .map(ProjectRepository.class::cast)
                .toList();
            return list.size() == 2;
        }));
        verify(projectGitHubInstallationAuthorizationRepository).deleteByProjectId(projectId);
        verify(gitHubAppInstallationRepository).delete(argThat(installation -> installation.getInstallationId().equals(20L)));
        verify(gitHubAppInstallationRepository).delete(argThat(installation -> installation.getInstallationId().equals(21L)));
    }

    private static ProjectRepository repositoryRow(UUID id, UUID projectId, String url, Long installationId) {
        ProjectRepository repository = new ProjectRepository();
        repository.setId(id);
        repository.setProjectId(projectId);
        repository.setProvider("github");
        repository.setRepositoryUrl(url);
        repository.setRepositoryName("repo");
        repository.setDefaultBranch("main");
        repository.setIsPrimary(true);
        repository.setInstallationId(installationId);
        repository.setCreatedAt(Instant.now());
        return repository;
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
