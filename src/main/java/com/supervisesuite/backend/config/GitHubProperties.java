package com.supervisesuite.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.github")
public class GitHubProperties {

    /**
     * Base URL for GitHub API, defaults to public API.
     */
    private String apiBaseUrl = "https://api.github.com";

    /**
     * Optional GitHub token for higher rate limits/private repos.
     */
    private String token;

    /**
     * GitHub App id used to generate app JWT.
     */
    private String appId;

    /**
     * GitHub App client id for setup/install flows.
     */
    private String appClientId;

    /**
     * GitHub App client secret for setup/install flows.
     */
    private String appClientSecret;

    /**
     * GitHub App install URL used to redirect users into GitHub authorization/setup.
     */
    private String appInstallUrl;

    /**
     * GitHub App private key content in PEM format.
     * Supports escaped new lines (\\n) from env files.
     */
    private String appPrivateKey;

    /**
     * GitHub webhook secret used for HMAC signature verification.
     */
    private String appWebhookSecret;

    /**
     * Default branch fallback used when provider metadata is missing.
     */
    private String defaultBranch = "main";

    /**
     * Activity recency window (hours) for deriving active vs idle status.
     */
    private int activityActiveWindowHours = 48;

    /**
     * Preview commit list size embedded in project details response.
     */
    private int previewCommitsLimit = 6;

    /**
     * Preview contributor list size embedded in project details response.
     */
    private int previewContributorsLimit = 4;

    /**
     * Contributor limit for non-paginated dashboard endpoint.
     */
    private int dashboardContributorsLimit = 5;

    /**
     * GitHub API per-page size used while fetching commit history.
     */
    private int commitsPageSize = 100;

    /**
     * Default page size for GitHub-related paginated API responses.
     */
    private int defaultPageSize = 10;

    /**
     * Upper bound for allowed page size in GitHub-related paginated APIs.
     */
    private int maxPageSize = 100;

    /**
     * Page-size settings for installation repositories listing.
     */
    private InstallationRepositories installationRepositories = new InstallationRepositories();

    /**
     * Settings for project-scoped access-request flow before GitHub redirect.
     */
    private AccessRequests accessRequests = new AccessRequests();

    @Getter
    @Setter
    public static class InstallationRepositories {
        /**
         * Default page size used when listing repositories under an installation.
         */
        private int defaultPageSize = 100;

        /**
         * Maximum allowed page size used when listing repositories under an installation.
         */
        private int maxPageSize = 100;
    }

    @Getter
    @Setter
    public static class AccessRequests {
        /**
         * Access request token lifetime in minutes.
         */
        private int expiresInMinutes = 15;
    }

}
