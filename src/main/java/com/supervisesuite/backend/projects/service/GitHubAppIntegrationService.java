package com.supervisesuite.backend.projects.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestContinueDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestValidationDto;
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
import com.supervisesuite.backend.projects.repository.ProjectRepositoryCacheRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GitHubAppIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubAppIntegrationService.class);

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_PENDING = "PENDING";

    private static final String ACCESS_REQUEST_STATUS_PENDING = "PENDING";
    private static final String ACCESS_REQUEST_STATUS_COMPLETED = "COMPLETED";
    private static final String ACCESS_REQUEST_STATUS_EXPIRED = "EXPIRED";

    private static final String ACCESS_REQUEST_INVALID_MESSAGE =
        "This access request link is invalid or has expired. Please create a new access request from the project.";

    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubAppInstallationRepository installationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository;
    private final ProjectRepositoryCacheRepository projectRepositoryCacheRepository;
    private final ProjectGitHubAccessRequestRepository projectGitHubAccessRequestRepository;
    private final GitHubProperties gitHubProperties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public GitHubAppIntegrationService(
        GitHubAppAuthService gitHubAppAuthService,
        GitHubAppInstallationRepository installationRepository,
        ProjectRepository projectRepository,
        ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository,
        ProjectRepositoryCacheRepository projectRepositoryCacheRepository,
        ProjectGitHubAccessRequestRepository projectGitHubAccessRequestRepository,
        GitHubProperties gitHubProperties,
        ObjectMapper objectMapper
    ) {
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.installationRepository = installationRepository;
        this.projectRepository = projectRepository;
        this.projectGitHubInstallationAuthorizationRepository = projectGitHubInstallationAuthorizationRepository;
        this.projectRepositoryCacheRepository = projectRepositoryCacheRepository;
        this.projectGitHubAccessRequestRepository = projectGitHubAccessRequestRepository;
        this.gitHubProperties = gitHubProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GitHubAccessRequestCreateDto createProjectAccessRequest(UUID projectId, UUID supervisorUserId) {
        if (projectId == null) {
            throw new ValidationException("projectId", "Project id is required.");
        }
        if (supervisorUserId == null) {
            throw new ValidationException("requestedByUserId", "Supervisor user id is required.");
        }

        Instant now = Instant.now();
        String rawToken = generateOpaqueToken();
        ProjectGitHubAccessRequest accessRequest = new ProjectGitHubAccessRequest();
        accessRequest.setProjectId(projectId);
        accessRequest.setRequestedBySupervisorUserId(supervisorUserId);
        accessRequest.setTokenHash(sha256Base64(rawToken));
        accessRequest.setStatus(ACCESS_REQUEST_STATUS_PENDING);
        accessRequest.setExpiresAt(now.plusSeconds((long) accessRequestExpiryMinutes() * 60L));
        accessRequest.setCreatedAt(now);
        accessRequest.setUpdatedAt(now);
        projectGitHubAccessRequestRepository.save(accessRequest);

        String requestUrl = "/github/request-access?token=" + rawToken;
        LOGGER.info(
            "Created GitHub access request projectId={} supervisorUserId={} expiresAt={}",
            projectId,
            supervisorUserId,
            accessRequest.getExpiresAt()
        );

        return new GitHubAccessRequestCreateDto(projectId, rawToken, requestUrl, accessRequest.getExpiresAt());
    }

    @Transactional
    public GitHubAccessRequestValidationDto validateProjectAccessRequest(
        UUID projectId,
        UUID supervisorUserId,
        String requestToken
    ) {
        Instant now = Instant.now();
        ProjectGitHubAccessRequest accessRequest = requirePendingAccessRequestByToken(
            projectId,
            supervisorUserId,
            requestToken,
            now
        );

        Project project = projectRepository
            .findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new ValidationException("projectId", "Project not found."));

        return new GitHubAccessRequestValidationDto(
            projectId,
            nullable(project.getName(), "Project"),
            accessRequest.getStatus(),
            accessRequest.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public GitHubAccessRequestValidationDto validateProjectAccessRequest(String requestToken) {
        Instant now = Instant.now();
        ProjectGitHubAccessRequest accessRequest = requirePendingAccessRequestByToken(requestToken, now);

        Project project = projectRepository
            .findByIdAndDeletedAtIsNull(accessRequest.getProjectId())
            .orElseThrow(() -> new ValidationException("token", ACCESS_REQUEST_INVALID_MESSAGE));

        return new GitHubAccessRequestValidationDto(
            accessRequest.getProjectId(),
            nullable(project.getName(), "Project"),
            accessRequest.getStatus(),
            accessRequest.getExpiresAt()
        );
    }

    @Transactional
    public GitHubAccessRequestContinueDto continueProjectAccessRequest(
        UUID projectId,
        UUID supervisorUserId,
        String requestToken
    ) {
        Instant now = Instant.now();
        ProjectGitHubAccessRequest accessRequest = requirePendingAccessRequestByToken(
            projectId,
            supervisorUserId,
            requestToken,
            now
        );

        String appInstallUrl = trimToNull(gitHubProperties.getAppInstallUrl());
        if (appInstallUrl == null) {
            throw new ValidationException(
                "GITHUB_APP_INSTALL_URL",
                "GitHub App install URL is not configured."
            );
        }

        String state = generateOpaqueToken();
        accessRequest.setGithubStateHash(sha256Base64(state));
        accessRequest.setUpdatedAt(now);
        projectGitHubAccessRequestRepository.save(accessRequest);

        String githubAuthorizeUrl = UriComponentsBuilder
            .fromUriString(appInstallUrl)
            .queryParam("state", state)
            .build(true)
            .toUriString();

        LOGGER.info(
            "Prepared GitHub access request redirect projectId={} supervisorUserId={} requestId={}",
            projectId,
            supervisorUserId,
            accessRequest.getId()
        );

        return new GitHubAccessRequestContinueDto(projectId, githubAuthorizeUrl);
    }

    @Transactional
    public GitHubAccessRequestContinueDto continueProjectAccessRequest(String requestToken) {
        Instant now = Instant.now();
        ProjectGitHubAccessRequest accessRequest = requirePendingAccessRequestByToken(requestToken, now);

        String appInstallUrl = trimToNull(gitHubProperties.getAppInstallUrl());
        if (appInstallUrl == null) {
            throw new ValidationException(
                "GITHUB_APP_INSTALL_URL",
                "GitHub App install URL is not configured."
            );
        }

        String state = generateOpaqueToken();
        accessRequest.setGithubStateHash(sha256Base64(state));
        accessRequest.setUpdatedAt(now);
        projectGitHubAccessRequestRepository.save(accessRequest);

        String githubAuthorizeUrl = UriComponentsBuilder
            .fromUriString(appInstallUrl)
            .queryParam("state", state)
            .build(true)
            .toUriString();

        LOGGER.info(
            "Prepared public GitHub access request redirect projectId={} requestId={}",
            accessRequest.getProjectId(),
            accessRequest.getId()
        );

        return new GitHubAccessRequestContinueDto(accessRequest.getProjectId(), githubAuthorizeUrl);
    }

    @Transactional
    public SetupCallbackResult handleSetupCallback(Long installationId, String state) {
        if (installationId == null || installationId < 1) {
            throw new ValidationException("installation_id", "GitHub installation_id is required.");
        }

        Instant now = Instant.now();
        ProjectGitHubAccessRequest accessRequest = resolveAccessRequestForCallback(state, now);
        UUID projectId = accessRequest != null ? accessRequest.getProjectId() : resolveLegacyProjectIdFromState(state);

        if (projectId == null) {
            throw new ValidationException("state", "Project id is required to complete GitHub setup.");
        }

        completeSetupCallback(installationId, projectId, now);
        refreshAccessibleRepositoriesSnapshot(installationId);

        if (accessRequest != null) {
            accessRequest.setStatus(ACCESS_REQUEST_STATUS_COMPLETED);
            accessRequest.setUsedAt(now);
            accessRequest.setInstallationId(installationId);
            accessRequest.setUpdatedAt(now);
            projectGitHubAccessRequestRepository.save(accessRequest);

            LOGGER.info(
                "Completed GitHub access request projectId={} requestId={} installationId={}",
                projectId,
                accessRequest.getId(),
                installationId
            );
        }

        return new SetupCallbackResult(projectId, installationId, accessRequest != null);
    }

    @Transactional
    public void handleSetupCallback(Long installationId, UUID projectId) {
        Instant now = Instant.now();
        completeSetupCallback(installationId, projectId, now);
        refreshAccessibleRepositoriesSnapshot(installationId);
    }

    @Transactional
    public GitHubWebhookResultDto handleWebhook(String event, String signature256, String payload) {
        if (!hasText(event)) {
            throw new ValidationException("X-GitHub-Event", "X-GitHub-Event header is required.");
        }
        verifyWebhookSignature(signature256, payload);

        try {
            JsonNode root = objectMapper.readTree(payload == null ? "{}" : payload);
            String action = textOrNull(root.path("action"));
            Long installationId = resolveInstallationId(root);
            Instant now = Instant.now();

            if (installationId == null || installationId < 1) {
                return new GitHubWebhookResultDto(event, action, null, "IGNORED");
            }

            if ("installation".equals(event)) {
                GitHubAppInstallation installation = upsertInstallationFromWebhook(root, installationId, now);
                String mappedStatus = mapInstallationStatus(action);
                installation.setStatus(mappedStatus);
                installation.setLastEventAt(now);
                installation.setUpdatedAt(now);
                installationRepository.save(installation);

                if (STATUS_DELETED.equals(mappedStatus)) {
                    clearInstallationLinkage(installationId, now);
                }
                return new GitHubWebhookResultDto(event, action, installationId, mappedStatus);
            }

            if ("installation_repositories".equals(event)) {
                GitHubAppInstallation installation = installationRepository
                    .findByInstallationId(installationId)
                    .orElseGet(() -> {
                        GitHubAppInstallation created = new GitHubAppInstallation();
                        created.setInstallationId(installationId);
                        created.setStatus(STATUS_PENDING);
                        created.setCreatedAt(now);
                        return created;
                    });
                if (!hasText(installation.getStatus())) {
                    installation.setStatus(STATUS_ACTIVE);
                }
                installation.setLastEventAt(now);
                installation.setUpdatedAt(now);
                installationRepository.save(installation);
                return new GitHubWebhookResultDto(event, action, installationId, installation.getStatus());
            }

            return new GitHubWebhookResultDto(event, action, installationId, "IGNORED");
        } catch (ValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceUnavailableException("Unable to process GitHub webhook event.", exception);
        }
    }

    private void completeSetupCallback(Long installationId, UUID projectId, Instant now) {
        if (installationId == null || installationId < 1) {
            throw new ValidationException("installation_id", "GitHub installation_id is required.");
        }
        if (projectId == null) {
            throw new ValidationException("state.projectId", "Project id is required to complete GitHub setup.");
        }

        GitHubAppAuthService.GitHubInstallationContext installationContext =
            gitHubAppAuthService.fetchInstallationContext(installationId);

        GitHubAppInstallation installation = installationRepository
            .findByInstallationId(installationId)
            .orElseGet(() -> {
                GitHubAppInstallation created = new GitHubAppInstallation();
                created.setInstallationId(installationId);
                created.setCreatedAt(now);
                return created;
            });

        installation.setAccountId(installationContext.accountId());
        installation.setAccountLogin(installationContext.accountLogin());
        installation.setAccountType(installationContext.accountType());
        installation.setStatus(STATUS_ACTIVE);
        if (installation.getInstalledAt() == null) {
            installation.setInstalledAt(now);
        }
        installation.setLastEventAt(now);
        installation.setUpdatedAt(now);
        installationRepository.save(installation);
        upsertProjectInstallationAuthorization(projectId, installationId, now);
    }

    private void refreshAccessibleRepositoriesSnapshot(Long installationId) {
        try {
            gitHubAppAuthService.fetchInstallationRepositories(installationId, 1, 1);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "GitHub installation repositories refresh failed installationId={}: {}",
                installationId,
                nullable(exception.getMessage(), "refresh failed")
            );
        }
    }

    private ProjectGitHubAccessRequest resolveAccessRequestForCallback(String state, Instant now) {
        String normalizedState = trimToNull(state);
        if (normalizedState == null) {
            return null;
        }

        String stateHash = sha256Base64(normalizedState);
        ProjectGitHubAccessRequest request = projectGitHubAccessRequestRepository
            .findByGithubStateHash(stateHash)
            .orElse(null);
        if (request == null) {
            return null;
        }

        if (!ACCESS_REQUEST_STATUS_PENDING.equals(request.getStatus()) || request.getUsedAt() != null) {
            throw new ValidationException("state", ACCESS_REQUEST_INVALID_MESSAGE);
        }

        if (request.getExpiresAt() == null || now.isAfter(request.getExpiresAt())) {
            markRequestExpired(request, now);
            throw new ValidationException("state", ACCESS_REQUEST_INVALID_MESSAGE);
        }

        return request;
    }

    private UUID resolveLegacyProjectIdFromState(String state) {
        String normalizedState = trimToNull(state);
        if (normalizedState == null) {
            return null;
        }

        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(normalizedState);
        } catch (IllegalArgumentException firstFailure) {
            try {
                decoded = Base64.getDecoder().decode(normalizedState);
            } catch (IllegalArgumentException secondFailure) {
                return null;
            }
        }

        try {
            JsonNode root = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
            String projectIdRaw = textOrNull(root.path("projectId"));
            return projectIdRaw == null ? null : UUID.fromString(projectIdRaw);
        } catch (Exception exception) {
            return null;
        }
    }

    private ProjectGitHubAccessRequest requirePendingAccessRequestByToken(
        UUID projectId,
        UUID supervisorUserId,
        String requestToken,
        Instant now
    ) {
        if (projectId == null) {
            throw new ValidationException("projectId", "Project id is required.");
        }
        if (supervisorUserId == null) {
            throw new ValidationException("requestedByUserId", "Supervisor user id is required.");
        }

        ProjectGitHubAccessRequest accessRequest = requirePendingAccessRequestByToken(requestToken, now);

        if (!projectId.equals(accessRequest.getProjectId())) {
            throw new ValidationException("token", ACCESS_REQUEST_INVALID_MESSAGE);
        }
        if (!supervisorUserId.equals(accessRequest.getRequestedBySupervisorUserId())) {
            throw new ValidationException("token", ACCESS_REQUEST_INVALID_MESSAGE);
        }

        return accessRequest;
    }

    private ProjectGitHubAccessRequest requirePendingAccessRequestByToken(
        String requestToken,
        Instant now
    ) {
        String normalizedToken = trimToNull(requestToken);
        if (normalizedToken == null) {
            throw new ValidationException("token", ACCESS_REQUEST_INVALID_MESSAGE);
        }

        ProjectGitHubAccessRequest accessRequest = projectGitHubAccessRequestRepository
            .findByTokenHash(sha256Base64(normalizedToken))
            .orElseThrow(() -> new ValidationException("token", ACCESS_REQUEST_INVALID_MESSAGE));

        if (!ACCESS_REQUEST_STATUS_PENDING.equals(accessRequest.getStatus()) || accessRequest.getUsedAt() != null) {
            throw new ValidationException("token", ACCESS_REQUEST_INVALID_MESSAGE);
        }
        if (accessRequest.getExpiresAt() == null || now.isAfter(accessRequest.getExpiresAt())) {
            markRequestExpired(accessRequest, now);
            throw new ValidationException("token", ACCESS_REQUEST_INVALID_MESSAGE);
        }

        return accessRequest;
    }

    private void markRequestExpired(ProjectGitHubAccessRequest accessRequest, Instant now) {
        accessRequest.setStatus(ACCESS_REQUEST_STATUS_EXPIRED);
        accessRequest.setUpdatedAt(now);
        projectGitHubAccessRequestRepository.save(accessRequest);
    }

    private int accessRequestExpiryMinutes() {
        GitHubProperties.AccessRequests config = gitHubProperties.getAccessRequests();
        if (config == null || config.getExpiresInMinutes() < 1) {
            throw new ValidationException(
                "app.github.access-requests.expires-in-minutes",
                "GitHub access request expiry configuration is invalid."
            );
        }
        return config.getExpiresInMinutes();
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Base64(String raw) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceUnavailableException("Unable to hash GitHub access request token.", exception);
        }
    }

    private GitHubAppInstallation upsertInstallationFromWebhook(JsonNode root, Long installationId, Instant now) {
        GitHubAppInstallation installation = installationRepository
            .findByInstallationId(installationId)
            .orElseGet(() -> {
                GitHubAppInstallation created = new GitHubAppInstallation();
                created.setInstallationId(installationId);
                created.setCreatedAt(now);
                created.setInstalledAt(now);
                return created;
            });

        JsonNode account = root.path("installation").path("account");
        if (account.isMissingNode() || account.isNull()) {
            account = root.path("account");
        }

        Long accountId = account.path("id").isIntegralNumber() ? account.path("id").asLong() : null;
        installation.setAccountId(accountId);
        installation.setAccountLogin(textOrNull(account.path("login")));
        installation.setAccountType(textOrNull(account.path("type")));
        return installation;
    }

    private void clearInstallationLinkage(Long installationId, Instant now) {
        List<com.supervisesuite.backend.projects.entity.ProjectRepository> repositories =
            projectRepositoryCacheRepository.findByInstallationId(installationId);
        if (repositories.isEmpty()) {
            return;
        }
        for (com.supervisesuite.backend.projects.entity.ProjectRepository repository : repositories) {
            repository.setInstallationId(null);
            repository.setRepositoryExternalId(null);
            repository.setOwnerLogin(null);
            repository.setLinkedBySupervisorUserId(null);
            repository.setLinkedAt(null);
            repository.setUpdatedAt(now);
        }
        projectRepositoryCacheRepository.saveAll(repositories);
        projectGitHubInstallationAuthorizationRepository.deleteByInstallationId(installationId);
    }

    private void upsertProjectInstallationAuthorization(UUID projectId, Long installationId, Instant now) {
        Project project = projectRepository
            .findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new ValidationException("projectId", "Project not found for GitHub setup."));

        UUID supervisorId = project.getSupervisor() == null ? null : project.getSupervisor().getId();
        if (supervisorId == null) {
            throw new ValidationException(
                "projectId",
                "Project does not have an assigned supervisor for GitHub authorization."
            );
        }

        ProjectGitHubInstallationAuthorization authorization = projectGitHubInstallationAuthorizationRepository
            .findByProjectIdAndInstallationId(projectId, installationId)
            .orElseGet(() -> {
                ProjectGitHubInstallationAuthorization created = new ProjectGitHubInstallationAuthorization();
                created.setProjectId(projectId);
                created.setInstallationId(installationId);
                created.setCreatedAt(now);
                return created;
            });

        authorization.setAuthorizedBySupervisorUserId(supervisorId);
        authorization.setAuthorizedAt(now);
        authorization.setUpdatedAt(now);
        projectGitHubInstallationAuthorizationRepository.save(authorization);
    }

    private String mapInstallationStatus(String action) {
        if (!hasText(action)) {
            return STATUS_ACTIVE;
        }

        String normalized = action.trim().toLowerCase(Locale.ROOT);
        if ("deleted".equals(normalized)) {
            return STATUS_DELETED;
        }
        if ("suspend".equals(normalized) || "suspended".equals(normalized)) {
            return STATUS_SUSPENDED;
        }
        return STATUS_ACTIVE;
    }

    private void verifyWebhookSignature(String signature256, String payload) {
        String webhookSecret = gitHubProperties.getAppWebhookSecret();
        if (!hasText(webhookSecret)) {
            throw new ValidationException(
                "GITHUB_APP_WEBHOOK_SECRET",
                "Webhook secret is not configured."
            );
        }
        if (!hasText(signature256)) {
            throw new ValidationException("X-Hub-Signature-256", "Missing webhook signature.");
        }

        String expected = "sha256=" + signSha256Hex(webhookSecret, payload == null ? "" : payload);
        if (!constantTimeEquals(expected, signature256.trim())) {
            throw new ValidationException("X-Hub-Signature-256", "Invalid webhook signature.");
        }
    }

    private String signSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new ServiceUnavailableException("Unable to verify webhook signature.", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.length() != right.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < left.length(); i++) {
            diff |= left.charAt(i) ^ right.charAt(i);
        }
        return diff == 0;
    }

    private Long resolveInstallationId(JsonNode root) {
        JsonNode installationNode = root.path("installation");
        if (installationNode.isObject() && installationNode.path("id").isIntegralNumber()) {
            return installationNode.path("id").asLong();
        }
        if (root.path("installation").isIntegralNumber()) {
            return root.path("installation").asLong();
        }
        if (root.path("installation_id").isIntegralNumber()) {
            return root.path("installation_id").asLong();
        }
        return null;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText();
        return hasText(text) ? text.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String nullable(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record SetupCallbackResult(UUID projectId, Long installationId, boolean requestFlowCompleted) {
    }
}
