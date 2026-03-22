package com.supervisesuite.backend.projects.integration.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
class GitHubCommitClientImpl implements GitHubCommitClient {

    private static final String GITHUB_HOST = "github.com";
    private static final String GITHUB_HOST_WWW = "www.github.com";
    private static final String GIT_SSH_PREFIX = "git@github.com:";
    private static final String USER_AGENT = "SuperviseSuite-Backend";
    private static final int COMMITS_PAGE_SIZE = 100;

    private final RestClient restClient;
    private final GitHubProperties gitHubProperties;

    GitHubCommitClientImpl(RestClient.Builder restClientBuilder, GitHubProperties gitHubProperties) {
        this.gitHubProperties = gitHubProperties;
        this.restClient = restClientBuilder
            .baseUrl(normalizeBaseUrl(gitHubProperties.getApiBaseUrl()))
            .build();
    }

    @Override
    public List<ProjectCommitDto> fetchRecentCommits(String repositoryUrl) {
        RepositoryRef ref = parseRepositoryRef(repositoryUrl);

        try {
            List<ProjectCommitDto> commits = new ArrayList<>();
            int page = 1;

            while (true) {
                final int currentPage = page;
                List<JsonNode> response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/commits")
                        .queryParam("per_page", COMMITS_PAGE_SIZE)
                        .queryParam("page", currentPage)
                        .build(ref.owner(), ref.repo()))
                    .headers(headers -> {
                        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                        headers.add(HttpHeaders.USER_AGENT, USER_AGENT);
                        if (hasText(gitHubProperties.getToken())) {
                            headers.setBearerAuth(gitHubProperties.getToken().trim());
                        }
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<JsonNode>>() {});

                if (response == null || response.isEmpty()) {
                    break;
                }

                for (JsonNode node : response) {
                    commits.add(mapCommit(node));
                }

                if (response.size() < COMMITS_PAGE_SIZE) {
                    break;
                }

                page++;
            }

            return commits;
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new ServiceUnavailableException(buildGitHubFailureMessage("commit activity", exception), exception);
        }
    }

    @Override
    public ProjectRepositoryMetadataDto fetchRepositoryMetadata(String repositoryUrl) {
        RepositoryRef ref = parseRepositoryRef(repositoryUrl);

        try {
            JsonNode response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/repos/{owner}/{repo}")
                    .build(ref.owner(), ref.repo()))
                .headers(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    headers.add(HttpHeaders.USER_AGENT, USER_AGENT);
                    if (hasText(gitHubProperties.getToken())) {
                        headers.setBearerAuth(gitHubProperties.getToken().trim());
                    }
                })
                .retrieve()
                .body(JsonNode.class);

            if (response == null) {
                return new ProjectRepositoryMetadataDto(ref.repo(), repositoryUrl, "main");
            }

            String name = textOrNull(response.path("name"));
            String url = textOrNull(response.path("html_url"));
            String defaultBranch = textOrNull(response.path("default_branch"));

            return new ProjectRepositoryMetadataDto(
                hasText(name) ? name.trim() : ref.repo(),
                hasText(url) ? url.trim() : repositoryUrl,
                hasText(defaultBranch) ? defaultBranch.trim() : "main"
            );
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new ServiceUnavailableException(
                buildGitHubFailureMessage("repository metadata", exception),
                exception
            );
        }
    }

    private String buildGitHubFailureMessage(String operation, Exception exception) {
        if (exception instanceof ResourceAccessException) {
            return "GitHub is currently unreachable. Please check network access and try again.";
        }

        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            String providerMessage = extractProviderMessage(responseException.getResponseBodyAsString());

            if (status == 404) {
                return "GitHub repository not found or inaccessible. Verify owner/repo URL and access.";
            }
            if (status == 401 || status == 403) {
                String base = "GitHub access denied or rate-limited. Verify GITHUB_TOKEN and repository access.";
                return hasText(providerMessage) ? base + " " + providerMessage : base;
            }

            String base = "GitHub " + operation + " request failed with status " + status + ".";
            return hasText(providerMessage) ? base + " " + providerMessage : base;
        }

        return "GitHub request failed. Please try again.";
    }

    private String extractProviderMessage(String body) {
        if (!hasText(body)) {
            return null;
        }

        try {
            JsonNode root = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(body);
            String message = textOrNull(root.path("message"));
            return hasText(message) ? message.trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ProjectCommitDto mapCommit(JsonNode node) {
        JsonNode commitNode = node.path("commit");

        String sha = textOrNull(node.path("sha"));
        String message = textOrEmpty(commitNode.path("message"));
        String author = resolveAuthor(node, commitNode);
        Instant committedAt = parseInstantOrNull(textOrNull(commitNode.path("author").path("date")));

        return new ProjectCommitDto(sha, message, author, committedAt);
    }

    private String resolveAuthor(JsonNode rootNode, JsonNode commitNode) {
        String commitAuthorName = textOrNull(commitNode.path("author").path("name"));
        if (hasText(commitAuthorName)) {
            return commitAuthorName.trim();
        }

        String githubLogin = textOrNull(rootNode.path("author").path("login"));
        if (hasText(githubLogin)) {
            return githubLogin.trim();
        }

        return "Unknown";
    }

    private RepositoryRef parseRepositoryRef(String repositoryUrl) {
        if (!hasText(repositoryUrl)) {
            throw new ValidationException("repositoryUrl", "Repository URL is required.");
        }

        String raw = repositoryUrl.trim();

        if (raw.startsWith(GIT_SSH_PREFIX)) {
            return parseSshRef(raw);
        }

        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if (!hasText(host)) {
                throw new ValidationException("repositoryUrl", "Repository URL is invalid.");
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (!GITHUB_HOST.equals(normalizedHost) && !GITHUB_HOST_WWW.equals(normalizedHost)) {
                throw new ValidationException("repositoryUrl", "Only GitHub repository URLs are supported.");
            }

            return parsePathRef(uri.getPath());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("repositoryUrl", "Repository URL is invalid.");
        }
    }

    private RepositoryRef parseSshRef(String sshUrl) {
        String path = sshUrl.substring(GIT_SSH_PREFIX.length()).trim();
        return parseOwnerRepo(path);
    }

    private RepositoryRef parsePathRef(String path) {
        if (path == null) {
            throw new ValidationException("repositoryUrl", "Repository URL is invalid.");
        }

        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return parseOwnerRepo(normalized);
    }

    private RepositoryRef parseOwnerRepo(String ownerRepoPath) {
        String[] segments = ownerRepoPath.split("/");
        if (segments.length < 2) {
            throw new ValidationException("repositoryUrl", "Repository URL must include owner and repository name.");
        }

        String owner = segments[0].trim();
        String repo = stripGitSuffix(segments[1].trim());

        if (!hasText(owner) || !hasText(repo)) {
            throw new ValidationException("repositoryUrl", "Repository URL must include owner and repository name.");
        }

        return new RepositoryRef(owner, repo);
    }

    private String stripGitSuffix(String value) {
        if (value.endsWith(".git")) {
            return value.substring(0, value.length() - 4);
        }
        return value;
    }

    private Instant parseInstantOrNull(String raw) {
        if (!hasText(raw)) {
            return null;
        }

        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return hasText(text) ? text : null;
    }

    private String textOrEmpty(JsonNode node) {
        String value = textOrNull(node);
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeBaseUrl(String value) {
        if (!hasText(value)) {
            return "https://api.github.com";
        }
        return value.trim();
    }

    private record RepositoryRef(String owner, String repo) {
    }
}
