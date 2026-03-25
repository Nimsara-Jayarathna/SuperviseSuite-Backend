package com.supervisesuite.backend.projects.service.githubv2;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.FrontendProperties;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateV2Dto;
import com.supervisesuite.backend.projects.entity.GitHubAccessRequestV2;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.GitHubAccessRequestV2Repository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessRequestService {

    private final GitHubAccessRequestV2Repository accessRequestRepository;
    private final GitHubIntegrationGuardService guardService;
    private final GitHubProperties gitHubProperties;
    private final FrontendProperties frontendProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccessRequestService(
        GitHubAccessRequestV2Repository accessRequestRepository,
        GitHubIntegrationGuardService guardService,
        GitHubProperties gitHubProperties,
        FrontendProperties frontendProperties
    ) {
        this.accessRequestRepository = accessRequestRepository;
        this.guardService = guardService;
        this.gitHubProperties = gitHubProperties;
        this.frontendProperties = frontendProperties;
    }

    @Transactional
    public GitHubAccessRequestCreateV2Dto createRequest(String projectIdRaw, String authenticatedUserIdRaw) {
        Project project = guardService.requireOwnedProject(projectIdRaw, authenticatedUserIdRaw);
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        Instant now = Instant.now();
        String rawToken = generateOpaqueToken();

        GitHubAccessRequestV2 accessRequest = new GitHubAccessRequestV2();
        accessRequest.setProjectId(project.getId());
        accessRequest.setRequestedByUserId(userId);
        accessRequest.setTokenHash(sha256Base64(rawToken));
        accessRequest.setExpiresAt(now.plusSeconds((long) accessRequestExpiryMinutes() * 60L));
        accessRequest.setCreatedAt(now);
        accessRequest.setUpdatedAt(now);
        accessRequestRepository.save(accessRequest);

        String requestUrl = buildRequestUrl(rawToken);
        return new GitHubAccessRequestCreateV2Dto(project.getId().toString(), requestUrl, accessRequest.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public GitHubAccessRequestV2 requireValidRequestToken(String requestToken) {
        String normalized = trimToNull(requestToken);
        if (normalized == null) {
            throw new ValidationException("token", "Access request token is invalid or expired.");
        }

        GitHubAccessRequestV2 accessRequest = accessRequestRepository
            .findByTokenHash(sha256Base64(normalized))
            .orElseThrow(() -> new ValidationException("token", "Access request token is invalid or expired."));

        Instant now = Instant.now();
        if (accessRequest.getUsedAt() != null || accessRequest.getExpiresAt() == null || now.isAfter(accessRequest.getExpiresAt())) {
            throw new ValidationException("token", "Access request token is invalid or expired.");
        }

        return accessRequest;
    }

    @Transactional
    public void markUsed(UUID accessRequestId) {
        if (accessRequestId == null) {
            return;
        }
        accessRequestRepository.findById(accessRequestId).ifPresent(request -> {
            request.setUsedAt(Instant.now());
            request.setUpdatedAt(Instant.now());
            accessRequestRepository.save(request);
        });
    }

    private int accessRequestExpiryMinutes() {
        GitHubProperties.AccessRequests accessRequests = gitHubProperties.getAccessRequests();
        if (accessRequests == null || accessRequests.getExpiresInMinutes() < 1) {
            return 15;
        }
        return accessRequests.getExpiresInMinutes();
    }

    private String buildRequestUrl(String rawToken) {
        String relative = "/github/request-access?token=" + rawToken;
        String frontendBaseUrl = trimToNull(frontendProperties.getBaseUrl());
        if (frontendBaseUrl == null) {
            return relative;
        }
        if (frontendBaseUrl.endsWith("/")) {
            return frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) + relative;
        }
        return frontendBaseUrl + relative;
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
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
