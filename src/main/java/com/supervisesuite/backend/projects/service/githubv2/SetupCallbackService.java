package com.supervisesuite.backend.projects.service.githubv2;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubInstallStartDto;
import com.supervisesuite.backend.projects.entity.GitHubAccessRequestV2;
import com.supervisesuite.backend.projects.entity.GitHubSetupState;
import com.supervisesuite.backend.projects.integration.github.GitHubClient;
import com.supervisesuite.backend.projects.repository.GitHubSetupStateRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetupCallbackService {

    private final GitHubSetupStateRepository setupStateRepository;
    private final GitHubProperties gitHubProperties;
    private final GitHubClient gitHubClient;
    private final AccessRequestService accessRequestService;
    private final GitHubIntegrationGuardService guardService;
    private final SecureRandom secureRandom = new SecureRandom();

    public SetupCallbackService(
        GitHubSetupStateRepository setupStateRepository,
        GitHubProperties gitHubProperties,
        GitHubClient gitHubClient,
        AccessRequestService accessRequestService,
        GitHubIntegrationGuardService guardService
    ) {
        this.setupStateRepository = setupStateRepository;
        this.gitHubProperties = gitHubProperties;
        this.gitHubClient = gitHubClient;
        this.accessRequestService = accessRequestService;
        this.guardService = guardService;
    }

    @Transactional
    public GitHubInstallStartDto startDirectInstall(String projectIdRaw, String authenticatedUserIdRaw) {
        UUID projectId = guardService.parseUuid(projectIdRaw, "projectId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");
        guardService.requireOwnedProject(projectId, userId);
        return createInstallState(projectId, userId, GitHubIntegrationV2Constants.FLOW_TYPE_INSTALLATION_DIRECT, null);
    }

    @Transactional
    public GitHubInstallStartDto startRequestedInstall(String requestToken) {
        GitHubAccessRequestV2 accessRequest = accessRequestService.requireValidRequestToken(requestToken);
        return createInstallState(
            accessRequest.getProjectId(),
            accessRequest.getRequestedByUserId(),
            GitHubIntegrationV2Constants.FLOW_TYPE_INSTALLATION_REQUESTED,
            accessRequest.getId()
        );
    }

    @Transactional
    public CallbackState consumeCallbackState(String stateToken) {
        if (stateToken == null || stateToken.trim().isEmpty()) {
            throw new ValidationException("state", "GitHub callback state is required.");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                .verifyWith(resolveSigningKey())
                .build()
                .parseSignedClaims(stateToken.trim())
                .getPayload();
        } catch (Exception exception) {
            throw new ValidationException("state", "GitHub callback state is invalid or expired.");
        }

        String jti = trimToNull(claims.getId());
        String projectIdRaw = trimToNull(claims.getSubject());
        String userIdRaw = trimToNull(claims.get("uid", String.class));
        String flowType = trimToNull(claims.get("flow", String.class));
        String requestIdRaw = trimToNull(claims.get("rid", String.class));

        if (jti == null || projectIdRaw == null || userIdRaw == null || flowType == null) {
            throw new ValidationException("state", "GitHub callback state is invalid or expired.");
        }

        UUID projectId = parseUuidSilently(projectIdRaw, "state.projectId");
        UUID userId = parseUuidSilently(userIdRaw, "state.userId");
        UUID requestId = requestIdRaw == null ? null : parseUuidSilently(requestIdRaw, "state.requestId");

        Instant now = Instant.now();
        GitHubSetupState setupState = setupStateRepository
            .findByStateJtiHash(sha256Base64(jti))
            .orElseThrow(() -> new ValidationException("state", "GitHub callback state is invalid or expired."));

        if (setupState.getUsedAt() != null || setupState.getExpiresAt() == null || now.isAfter(setupState.getExpiresAt())) {
            throw new ValidationException("state", "GitHub callback state is invalid or expired.");
        }

        if (!projectId.equals(setupState.getProjectId()) || !userId.equals(setupState.getUserId())) {
            throw new ValidationException("state", "GitHub callback state is invalid or expired.");
        }

        if (!flowType.equals(setupState.getFlowType())) {
            throw new ValidationException("state", "GitHub callback state is invalid or expired.");
        }

        if (requestId == null && setupState.getRequestId() != null) {
            throw new ValidationException("state", "GitHub callback state is invalid or expired.");
        }
        if (requestId != null && !requestId.equals(setupState.getRequestId())) {
            throw new ValidationException("state", "GitHub callback state is invalid or expired.");
        }

        setupState.setUsedAt(now);
        setupState.setUpdatedAt(now);
        setupStateRepository.save(setupState);

        return new CallbackState(projectId, userId, flowType, requestId);
    }

    private GitHubInstallStartDto createInstallState(UUID projectId, UUID userId, String flowType, UUID requestId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(Math.max(60, gitHubProperties.getSetupStateTtlSeconds()));
        String jti = generateOpaqueToken();

        String stateToken = Jwts.builder()
            .id(jti)
            .subject(projectId.toString())
            .claim("uid", userId.toString())
            .claim("flow", flowType)
            .claim("rid", requestId == null ? null : requestId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(resolveSigningKey())
            .compact();

        GitHubSetupState setupState = new GitHubSetupState();
        setupState.setStateJtiHash(sha256Base64(jti));
        setupState.setProjectId(projectId);
        setupState.setUserId(userId);
        setupState.setFlowType(flowType);
        setupState.setRequestId(requestId);
        setupState.setExpiresAt(expiresAt);
        setupState.setCreatedAt(now);
        setupState.setUpdatedAt(now);
        setupStateRepository.save(setupState);

        String githubAuthorizeUrl = gitHubClient.buildInstallUrl(stateToken);
        return new GitHubInstallStartDto(projectId.toString(), githubAuthorizeUrl, flowType, expiresAt);
    }

    private SecretKey resolveSigningKey() {
        String configuredSecret = trimToNull(gitHubProperties.getSetupStateSecret());
        if (configuredSecret == null) {
            throw new ValidationException("GITHUB_SETUP_STATE_SECRET", "Setup state secret is not configured.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(configuredSecret);
        } catch (IllegalArgumentException exception) {
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            keyBytes = sha256Bytes(keyBytes);
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    private UUID parseUuidSilently(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(field, "GitHub callback state is invalid or expired.");
        }
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Base64(String raw) {
        return Base64.getEncoder().encodeToString(sha256Bytes((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] sha256Bytes(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    public record CallbackState(UUID projectId, UUID userId, String flowType, UUID requestId) {
    }
}
