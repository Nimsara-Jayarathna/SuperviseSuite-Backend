package com.supervisesuite.backend.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestContinueDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestValidationDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedAcknowledgeDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedSummaryDto;
import com.supervisesuite.backend.projects.dto.GitHubWebhookResultDto;
import com.supervisesuite.backend.projects.entity.GitHubAppInstallation;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectGitHubAccessRequest;
import com.supervisesuite.backend.projects.entity.ProjectGitHubInstallationAuthorization;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import com.supervisesuite.backend.projects.repository.GitHubAppInstallationRepository;
import com.supervisesuite.backend.projects.repository.ProjectGitHubAccessRequestRepository;
import com.supervisesuite.backend.projects.repository.ProjectGitHubInstallationAuthorizationRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.users.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubAppIntegrationServiceTest {

    @Mock
    private GitHubAppAuthService gitHubAppAuthService;

    @Mock
    private GitHubAppInstallationRepository installationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository;


    @Mock
    private ProjectGitHubAccessRequestRepository projectGitHubAccessRequestRepository;

    @Mock
    private RepositoryLinkService repositoryLinkService;

    private GitHubProperties gitHubProperties;
    private GitHubAppIntegrationService service;

    private UUID projectId;
    private UUID supervisorUserId;

    @BeforeEach
    void setUp() {
        gitHubProperties = new GitHubProperties();
        gitHubProperties.setAppInstallUrl("https://github.com/apps/supervisesuite/installations/new");
        gitHubProperties.setAppWebhookSecret("test-webhook-secret");
        gitHubProperties.setDefaultBranch("main");

        GitHubProperties.AccessRequests accessRequests = new GitHubProperties.AccessRequests();
        accessRequests.setExpiresInMinutes(10);
        gitHubProperties.setAccessRequests(accessRequests);

        GitHubProperties.InstallationRepositories installationRepositories = new GitHubProperties.InstallationRepositories();
        installationRepositories.setDefaultPageSize(2);
        installationRepositories.setMaxPageSize(2);
        gitHubProperties.setInstallationRepositories(installationRepositories);

        service = new GitHubAppIntegrationService(
            gitHubAppAuthService,
            installationRepository,
            projectRepository,
            projectGitHubInstallationAuthorizationRepository,
            projectGitHubAccessRequestRepository,
            repositoryLinkService,
            gitHubProperties,
            new ObjectMapper()
        );

        projectId = UUID.randomUUID();
        supervisorUserId = UUID.randomUUID();
    }

    @Test
    void createProjectAccessRequest_savesPendingRequestWithHashedToken() {
        when(projectGitHubAccessRequestRepository.save(any(ProjectGitHubAccessRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        GitHubAccessRequestCreateDto result = service.createProjectAccessRequest(projectId, supervisorUserId);

        assertThat(result.getProjectId()).isEqualTo(projectId);
        assertThat(result.getRequestToken()).isNotBlank();
        assertThat(result.getRequestUrl()).contains(result.getRequestToken());
        assertThat(result.getExpiresAt()).isAfter(Instant.now());

        verify(projectGitHubAccessRequestRepository).save(argThat(request ->
            request.getProjectId().equals(projectId)
                && request.getRequestedBySupervisorUserId().equals(supervisorUserId)
                && "PENDING".equals(request.getStatus())
                && request.getTokenHash() != null
                && !request.getTokenHash().equals(result.getRequestToken())
        ));
    }

    @Test
    void validateProjectAccessRequest_returnsProjectAndExpiry() {
        String rawToken = "request-token";
        ProjectGitHubAccessRequest request = pendingRequest(projectId, supervisorUserId, rawToken);
        Project project = project(projectId, "Capstone");

        when(projectGitHubAccessRequestRepository.findByTokenHash(sha256Base64(rawToken)))
            .thenReturn(Optional.of(request));
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
            .thenReturn(Optional.of(project));

        GitHubAccessRequestValidationDto result = service.validateProjectAccessRequest(rawToken);

        assertThat(result.getProjectId()).isEqualTo(projectId);
        assertThat(result.getProjectTitle()).isEqualTo("Capstone");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getExpiresAt()).isEqualTo(request.getExpiresAt());
    }

    @Test
    void continueProjectAccessRequest_generatesStateAndAuthorizeUrl() {
        String rawToken = "request-token";
        ProjectGitHubAccessRequest request = pendingRequest(projectId, supervisorUserId, rawToken);

        when(projectGitHubAccessRequestRepository.findByTokenHash(sha256Base64(rawToken)))
            .thenReturn(Optional.of(request));
        when(projectGitHubAccessRequestRepository.save(any(ProjectGitHubAccessRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        GitHubAccessRequestContinueDto result = service.continueProjectAccessRequest(rawToken);

        assertThat(result.getProjectId()).isEqualTo(projectId);
        assertThat(result.getGithubAuthorizeUrl()).startsWith("https://github.com/apps/supervisesuite/installations/new");
        assertThat(result.getGithubAuthorizeUrl()).contains("state=");
        assertThat(request.getGithubStateHash()).isNotBlank();
    }


    @Test
    void handleSetupCallback_marksAccessRequestCompletedAndReturnsResultToken() {
        Long installationId = 77L;
        String state = "setup-state";
        ProjectGitHubAccessRequest request = pendingRequest(projectId, supervisorUserId, "request-token");
        request.setGithubStateHash(sha256Base64(state));

        when(projectGitHubAccessRequestRepository.findByGithubStateHash(sha256Base64(state)))
            .thenReturn(Optional.of(request));
        when(projectGitHubAccessRequestRepository.save(any(ProjectGitHubAccessRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
            .thenReturn(Optional.of(projectWithSupervisor(projectId, supervisorUserId, "Capstone")));
        when(gitHubAppAuthService.fetchInstallationContext(installationId))
            .thenReturn(new GitHubAppAuthService.GitHubInstallationContext(
                installationId,
                10L,
                "acme",
                "Organization"
            ));
        when(installationRepository.findByInstallationId(installationId))
            .thenReturn(Optional.empty());
        when(installationRepository.save(any(GitHubAppInstallation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectGitHubInstallationAuthorizationRepository.findByProjectIdAndInstallationId(projectId, installationId))
            .thenReturn(Optional.empty());
        when(projectGitHubInstallationAuthorizationRepository.save(any(ProjectGitHubInstallationAuthorization.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(gitHubAppAuthService.fetchInstallationRepositories(installationId, 1, 1))
            .thenReturn(new GitHubAppAuthService.GitHubInstallationRepositoriesPageContext(List.of(), 0L));

        GitHubAppIntegrationService.SetupCallbackResult result = service.handleSetupCallback(installationId, state);

        assertThat(result.projectId()).isEqualTo(projectId);
        assertThat(result.installationId()).isEqualTo(installationId);
        assertThat(result.requestFlowCompleted()).isTrue();
        assertThat(result.resultToken()).isNotBlank();

        assertThat(request.getStatus()).isEqualTo("COMPLETED");
        assertThat(request.getUsedAt()).isNotNull();
        assertThat(request.getInstallationId()).isEqualTo(installationId);
        assertThat(request.getResultTokenHash()).isNotNull();
        assertThat(request.getResultExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void getAccessUpdatedSummary_returnsPaginatedRepositoriesAndScope() {
        String rawResultToken = "result-token";
        Long installationId = 88L;
        ProjectGitHubAccessRequest request = completedRequest(projectId, supervisorUserId, rawResultToken, installationId);

        when(projectGitHubAccessRequestRepository.findByResultTokenHash(sha256Base64(rawResultToken)))
            .thenReturn(Optional.of(request));
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
            .thenReturn(Optional.of(project(projectId, "Repository Sync")));
        when(gitHubAppAuthService.fetchInstallationRepositories(installationId, 1, 2))
            .thenReturn(new GitHubAppAuthService.GitHubInstallationRepositoriesPageContext(
                List.of(
                    new GitHubAppAuthService.GitHubInstallationRepositoryContext(
                        1L,
                        "repo-one",
                        "acme/repo-one",
                        "acme",
                        "https://github.com/acme/repo-one",
                        "main"
                    ),
                    new GitHubAppAuthService.GitHubInstallationRepositoryContext(
                        2L,
                        "repo-two",
                        "acme/repo-two",
                        "acme",
                        "https://github.com/acme/repo-two",
                        "main"
                    )
                ),
                3L
            ));
        when(gitHubAppAuthService.fetchInstallationRepositories(installationId, 2, 2))
            .thenReturn(new GitHubAppAuthService.GitHubInstallationRepositoriesPageContext(
                List.of(
                    new GitHubAppAuthService.GitHubInstallationRepositoryContext(
                        3L,
                        "repo-three",
                        "acme/repo-three",
                        "acme",
                        "https://github.com/acme/repo-three",
                        null
                    )
                ),
                3L
            ));

        GitHubAccessUpdatedSummaryDto result = service.getAccessUpdatedSummary(rawResultToken);

        assertThat(result.getProjectId()).isEqualTo(projectId);
        assertThat(result.getProjectTitle()).isEqualTo("Repository Sync");
        assertThat(result.getInstallationId()).isEqualTo(installationId);
        assertThat(result.getAccessibleRepositoryCount()).isEqualTo(3);
        assertThat(result.getAccessScope()).isEqualTo("MULTIPLE_REPOSITORIES");
        assertThat(result.getRepositories()).hasSize(3);
    }

    @Test
    void acknowledgeAccessUpdated_clearsResultTokenFields() {
        String rawResultToken = "result-token";
        Long installationId = 91L;
        ProjectGitHubAccessRequest request = completedRequest(projectId, supervisorUserId, rawResultToken, installationId);

        when(projectGitHubAccessRequestRepository.findByResultTokenHash(sha256Base64(rawResultToken)))
            .thenReturn(Optional.of(request));
        when(projectGitHubAccessRequestRepository.save(any(ProjectGitHubAccessRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        GitHubAccessUpdatedAcknowledgeDto result = service.acknowledgeAccessUpdated(rawResultToken);

        assertThat(result.getProjectId()).isEqualTo(projectId);
        assertThat(request.getResultTokenHash()).isNull();
        assertThat(request.getResultExpiresAt()).isNull();
        assertThat(request.getResultAcknowledgedAt()).isNotNull();
    }

    @Test
    void handleWebhook_rejectsInvalidSignature() {
        assertThatThrownBy(() -> service.handleWebhook("installation", "sha256=bad", "{}"))
            .isInstanceOfSatisfying(ValidationException.class, exception -> {
                assertThat(exception.getDetails()).anyMatch(detail ->
                    "X-Hub-Signature-256".equals(detail.getField())
                        && detail.getIssue().contains("Invalid webhook signature")
                );
            });
    }

    @Test
    void handleWebhook_installationDeleted_clearsLinkage() {
        Long installationId = 55L;
        String payload = """
            {
              "action": "deleted",
              "installation": {
                "id": 55,
                "account": {
                  "id": 9,
                  "login": "acme",
                  "type": "Organization"
                }
              }
            }
            """;
        String signature = sign(gitHubProperties.getAppWebhookSecret(), payload);

        GitHubAppInstallation existingInstallation = new GitHubAppInstallation();
        existingInstallation.setInstallationId(installationId);
        existingInstallation.setStatus("ACTIVE");
        existingInstallation.setCreatedAt(Instant.now().minusSeconds(100));

        when(installationRepository.findByInstallationId(installationId))
            .thenReturn(Optional.of(existingInstallation));
        when(installationRepository.save(any(GitHubAppInstallation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        GitHubWebhookResultDto result = service.handleWebhook("installation", signature, payload);

        assertThat(result.getStatus()).isEqualTo("DELETED");
        verify(repositoryLinkService).disconnectAllLinksByInstallationId(installationId);
        verify(projectGitHubInstallationAuthorizationRepository).deleteByInstallationId(installationId);
    }

    private static Project project(UUID id, String title) {
        Project project = new Project();
        project.setId(id);
        project.setName(title);
        project.setCreatedAt(Instant.now().minusSeconds(120));
        project.setStatus("ACTIVE");
        return project;
    }

    private static Project projectWithSupervisor(UUID id, UUID supervisorId, String title) {
        Project project = project(id, title);
        User supervisor = new User();
        supervisor.setId(supervisorId);
        supervisor.setEmail("supervisor@university.ac.lk");
        project.setSupervisor(supervisor);
        return project;
    }

    private static ProjectGitHubAccessRequest pendingRequest(UUID projectId, UUID supervisorUserId, String rawToken) {
        ProjectGitHubAccessRequest request = new ProjectGitHubAccessRequest();
        request.setId(UUID.randomUUID());
        request.setProjectId(projectId);
        request.setRequestedBySupervisorUserId(supervisorUserId);
        request.setTokenHash(sha256Base64(rawToken));
        request.setStatus("PENDING");
        request.setExpiresAt(Instant.now().plusSeconds(600));
        request.setCreatedAt(Instant.now().minusSeconds(30));
        request.setUpdatedAt(Instant.now().minusSeconds(30));
        return request;
    }

    private static ProjectGitHubAccessRequest completedRequest(
        UUID projectId,
        UUID supervisorUserId,
        String rawResultToken,
        Long installationId
    ) {
        ProjectGitHubAccessRequest request = pendingRequest(projectId, supervisorUserId, "pending-token");
        request.setStatus("COMPLETED");
        request.setUsedAt(Instant.now().minusSeconds(30));
        request.setInstallationId(installationId);
        request.setResultTokenHash(sha256Base64(rawResultToken));
        request.setResultExpiresAt(Instant.now().plusSeconds(600));
        return request;
    }

    private static String sha256Base64(String raw) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash value for test setup.", exception);
        }
    }

    private static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign payload for test setup.", exception);
        }
    }
}
