package com.supervisesuite.backend.projects.integration.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GitHubAppAuthServiceImpl implements GitHubAppAuthService {

    private static final String USER_AGENT = "SuperviseSuite-Backend";
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();

    private final GitHubProperties gitHubProperties;
    private final RestClient restClient;

    public GitHubAppAuthServiceImpl(GitHubProperties gitHubProperties, RestClient.Builder restClientBuilder) {
        this.gitHubProperties = gitHubProperties;
        this.restClient = restClientBuilder
            .baseUrl(normalizeBaseUrl(gitHubProperties.getApiBaseUrl()))
            .build();
    }

    @Override
    public String createAppJwt() {
        String appId = required(gitHubProperties.getAppId(), "GITHUB_APP_ID");
        String privateKeyValue = required(gitHubProperties.getAppPrivateKey(), "GITHUB_APP_PRIVATE_KEY");
        String privateKeyPem = resolvePrivateKeyPem(privateKeyValue);

        try {
            PrivateKey privateKey = parsePrivateKey(privateKeyPem);
            Instant now = Instant.now();
            return Jwts.builder()
                .issuer(appId.trim())
                .issuedAt(Date.from(now.minusSeconds(60)))
                .expiration(Date.from(now.plusSeconds(540)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        } catch (ValidationException exception) {
            throw exception;
        } catch (SignatureException exception) {
            throw new ValidationException("githubAppPrivateKey", "Invalid GitHub App private key.");
        } catch (Exception exception) {
            String issue = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Unable to generate GitHub App JWT."
                : "Unable to generate GitHub App JWT: " + exception.getMessage();
            throw new ValidationException("githubApp", issue);
        }
    }

    @Override
    public GitHubInstallationToken createInstallationAccessToken(Long installationId) {
        if (installationId == null || installationId < 1) {
            throw new ValidationException("installationId", "Installation id is required.");
        }

        try {
            String appJwt = createAppJwt();
            JsonNode response = restClient
                .post()
                .uri("/app/installations/{installationId}/access_tokens", installationId)
                .headers(headers -> {
                    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                    headers.add(HttpHeaders.USER_AGENT, USER_AGENT);
                    headers.setBearerAuth(appJwt);
                })
                .retrieve()
                .body(JsonNode.class);

            if (response == null || response.path("token").isMissingNode()) {
                throw new ServiceUnavailableException("GitHub installation token response was empty.");
            }

            String token = textOrNull(response.path("token"));
            Instant expiresAt = parseInstantOrNull(textOrNull(response.path("expires_at")));
            if (token == null) {
                throw new ServiceUnavailableException("GitHub installation token was not returned.");
            }
            return new GitHubInstallationToken(token, expiresAt);
        } catch (ValidationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw buildInstallationTokenException(exception);
        } catch (ResourceAccessException exception) {
            throw new ServiceUnavailableException(
                "GitHub is currently unreachable. Check network connectivity and try again.",
                exception
            );
        }
    }

    @Override
    public GitHubInstallationContext fetchInstallationContext(Long installationId) {
        if (installationId == null || installationId < 1) {
            throw new ValidationException("installationId", "Installation id is required.");
        }

        try {
            String appJwt = createAppJwt();
            JsonNode response = restClient
                .get()
                .uri("/app/installations/{installationId}", installationId)
                .headers(headers -> {
                    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                    headers.add(HttpHeaders.USER_AGENT, USER_AGENT);
                    headers.setBearerAuth(appJwt);
                })
                .retrieve()
                .body(JsonNode.class);

            if (response == null) {
                return new GitHubInstallationContext(installationId, null, null, null);
            }

            JsonNode account = response.path("account");
            return new GitHubInstallationContext(
                response.path("id").isIntegralNumber() ? response.path("id").asLong() : installationId,
                account.path("id").isIntegralNumber() ? account.path("id").asLong() : null,
                textOrNull(account.path("login")),
                textOrNull(account.path("type"))
            );
        } catch (ValidationException exception) {
            throw exception;
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new ServiceUnavailableException("Unable to fetch GitHub installation details.", exception);
        }
    }

    @Override
    public GitHubInstallationRepositoriesPageContext fetchInstallationRepositories(Long installationId, int page, int size) {
        if (installationId == null || installationId < 1) {
            throw new ValidationException("installationId", "Installation id is required.");
        }
        if (page < 1) {
            throw new ValidationException("page", "Page must be greater than zero.");
        }
        if (size < 1) {
            throw new ValidationException("size", "Size must be greater than zero.");
        }

        try {
            GitHubInstallationToken installationToken = createInstallationAccessToken(installationId);
            JsonNode response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/installation/repositories")
                    .queryParam("per_page", size)
                    .queryParam("page", page)
                    .build())
                .headers(headers -> {
                    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                    headers.add(HttpHeaders.USER_AGENT, USER_AGENT);
                    headers.setBearerAuth(installationToken.token());
                })
                .retrieve()
                .body(JsonNode.class);

            JsonNode repositoriesNode = response == null ? null : response.path("repositories");
            if (repositoriesNode == null || !repositoriesNode.isArray()) {
                return new GitHubInstallationRepositoriesPageContext(List.of(), null);
            }

            List<GitHubInstallationRepositoryContext> repositories = new ArrayList<>();
            for (JsonNode repo : repositoriesNode) {
                JsonNode owner = repo.path("owner");
                repositories.add(new GitHubInstallationRepositoryContext(
                    repo.path("id").isIntegralNumber() ? repo.path("id").asLong() : null,
                    textOrNull(repo.path("name")),
                    textOrNull(repo.path("full_name")),
                    textOrNull(owner.path("login")),
                    textOrNull(repo.path("html_url")),
                    textOrNull(repo.path("default_branch"))
                ));
            }
            Long totalCount = response != null && response.path("total_count").isIntegralNumber()
                ? response.path("total_count").asLong()
                : null;

            return new GitHubInstallationRepositoriesPageContext(repositories, totalCount);
        } catch (ValidationException exception) {
            throw exception;
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new ServiceUnavailableException("Unable to fetch installation repositories.", exception);
        }
    }

    private PrivateKey parsePrivateKey(String rawPem) throws Exception {
        String normalized = rawPem.trim().replace("\\n", "\n");
        boolean isPkcs1Pem = normalized.contains("BEGIN RSA PRIVATE KEY");
        String cleaned = normalized
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(cleaned);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        try {
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (InvalidKeySpecException invalidPkcs8) {
            if (!isPkcs1Pem) {
                throw new ValidationException(
                    "GITHUB_APP_PRIVATE_KEY",
                    "Private key is invalid. Use PEM with BEGIN PRIVATE KEY or BEGIN RSA PRIVATE KEY."
                );
            }
            byte[] pkcs8Bytes = wrapPkcs1AsPkcs8(decoded);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Bytes));
        }
    }

    private String resolvePrivateKeyPem(String configuredValue) {
        String trimmed = configuredValue == null ? "" : configuredValue.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("GITHUB_APP_PRIVATE_KEY", "GITHUB_APP_PRIVATE_KEY is not configured.");
        }

        if (trimmed.contains("BEGIN PRIVATE KEY") || trimmed.contains("BEGIN RSA PRIVATE KEY")) {
            return trimmed;
        }

        try {
            Path path = Path.of(trimmed);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (Exception exception) {
            throw new ValidationException(
                "GITHUB_APP_PRIVATE_KEY",
                "Unable to read private key file from path: " + trimmed
            );
        }

        throw new ValidationException(
            "GITHUB_APP_PRIVATE_KEY",
            "Provide PEM key content or a valid private key file path."
        );
    }

    private byte[] wrapPkcs1AsPkcs8(byte[] pkcs1Bytes) {
        byte[] version = new byte[] { 0x02, 0x01, 0x00 };
        byte[] algorithmIdentifier = new byte[] {
            0x30, 0x0d,
            0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
            0x05, 0x00
        };
        byte[] privateKeyOctetString = derEncode((byte) 0x04, pkcs1Bytes);
        return derEncodeSequence(version, algorithmIdentifier, privateKeyOctetString);
    }

    private byte[] derEncodeSequence(byte[]... parts) {
        int totalLength = 0;
        for (byte[] part : parts) {
            totalLength += part.length;
        }

        List<byte[]> chunks = new ArrayList<>();
        chunks.add(new byte[] { 0x30 });
        chunks.add(derLength(totalLength));
        chunks.addAll(List.of(parts));
        return concat(chunks.toArray(new byte[0][]));
    }

    private byte[] derEncode(byte tag, byte[] value) {
        return concat(new byte[] { tag }, derLength(value.length), value);
    }

    private byte[] derLength(int length) {
        if (length < 128) {
            return new byte[] { (byte) length };
        }

        int temp = length;
        int numberOfBytes = 0;
        while (temp > 0) {
            numberOfBytes++;
            temp >>= 8;
        }

        byte[] result = new byte[1 + numberOfBytes];
        result[0] = (byte) (0x80 | numberOfBytes);
        for (int i = numberOfBytes; i > 0; i--) {
            result[i] = (byte) (length & 0xff);
            length >>= 8;
        }
        return result;
    }

    private byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }
        byte[] output = new byte[totalLength];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, output, offset, array.length);
            offset += array.length;
        }
        return output;
    }

    private String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(fieldName, fieldName + " is not configured.");
        }
        return value;
    }

    private RuntimeException buildInstallationTokenException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String message = buildInstallationTokenFailureMessage(exception);

        if (status == 401 || status == 403 || status == 404 || status == 422) {
            return new GitHubInstallationDisconnectedException(message, exception);
        }

        return new ServiceUnavailableException(message, exception);
    }

    private String buildInstallationTokenFailureMessage(RestClientResponseException responseException) {
        int status = responseException.getStatusCode().value();
        String providerMessage = extractProviderMessage(responseException.getResponseBodyAsString());

        if (status == 401 || status == 403) {
            String base =
                "GitHub App installation authorization is no longer valid. Reconnect the GitHub App in Overview and try again.";
            return withProviderMessage(base, providerMessage);
        }
        if (status == 404) {
            String base =
                "GitHub App installation was not found. It may have been removed from GitHub. Reconnect the GitHub App and relink the repository.";
            return withProviderMessage(base, providerMessage);
        }
        if (status == 422) {
            String base =
                "GitHub rejected the installation token request. Reconnect the GitHub App and verify installation permissions.";
            return withProviderMessage(base, providerMessage);
        }

        String base = "Unable to create GitHub installation token (status " + status + ").";
        return withProviderMessage(base, providerMessage);
    }

    private String extractProviderMessage(String body) {
        if (!hasText(body)) {
            return null;
        }

        try {
            JsonNode root = JSON_MAPPER.readTree(body);
            String message = textOrNull(root.path("message"));
            if (hasText(message)) {
                return message.trim();
            }

            JsonNode errors = root.path("errors");
            if (errors.isArray()) {
                for (JsonNode error : errors) {
                    String errorMessage = textOrNull(error.path("message"));
                    if (hasText(errorMessage)) {
                        return errorMessage.trim();
                    }
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String withProviderMessage(String base, String providerMessage) {
        if (!hasText(providerMessage)) {
            return base;
        }
        return base + " GitHub: " + providerMessage;
    }

    private Instant parseInstantOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("GITHUB_API_BASE_URL", "GITHUB_API_BASE_URL is not configured.");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
