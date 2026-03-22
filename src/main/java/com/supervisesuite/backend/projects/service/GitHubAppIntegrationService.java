package com.supervisesuite.backend.projects.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubWebhookResultDto;
import com.supervisesuite.backend.projects.entity.GitHubAppInstallation;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectGitHubInstallationAuthorization;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import com.supervisesuite.backend.projects.repository.GitHubAppInstallationRepository;
import com.supervisesuite.backend.projects.repository.ProjectGitHubInstallationAuthorizationRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryCacheRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubAppIntegrationService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_PENDING = "PENDING";

    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubAppInstallationRepository installationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository;
    private final ProjectRepositoryCacheRepository projectRepositoryCacheRepository;
    private final GitHubProperties gitHubProperties;
    private final ObjectMapper objectMapper;

    public GitHubAppIntegrationService(
        GitHubAppAuthService gitHubAppAuthService,
        GitHubAppInstallationRepository installationRepository,
        ProjectRepository projectRepository,
        ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository,
        ProjectRepositoryCacheRepository projectRepositoryCacheRepository,
        GitHubProperties gitHubProperties,
        ObjectMapper objectMapper
    ) {
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.installationRepository = installationRepository;
        this.projectRepository = projectRepository;
        this.projectGitHubInstallationAuthorizationRepository = projectGitHubInstallationAuthorizationRepository;
        this.projectRepositoryCacheRepository = projectRepositoryCacheRepository;
        this.gitHubProperties = gitHubProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handleSetupCallback(Long installationId, java.util.UUID projectId) {
        if (installationId == null || installationId < 1) {
            throw new ValidationException("installation_id", "GitHub installation_id is required.");
        }
        if (projectId == null) {
            throw new ValidationException("state.projectId", "Project id is required to complete GitHub setup.");
        }

        GitHubAppAuthService.GitHubInstallationContext installationContext =
            gitHubAppAuthService.fetchInstallationContext(installationId);

        Instant now = Instant.now();
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

    private void upsertProjectInstallationAuthorization(java.util.UUID projectId, Long installationId, Instant now) {
        Project project = projectRepository
            .findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new ValidationException("projectId", "Project not found for GitHub setup."));

        java.util.UUID supervisorId = project.getSupervisor() == null ? null : project.getSupervisor().getId();
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
}
