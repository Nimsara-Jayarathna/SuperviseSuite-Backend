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

    private int maxLinkedReposPerProject = 5;

    /**
     * Maximum linked repositories that can stay enabled at once in a project.
     */
    private int maxEnabledReposPerProject = 5;

    private int setupStateTtlSeconds = 900;

    private String setupStateSecret;

    private int syncMaxCommitPages = 5;

    /**
     * Page-size settings for installation repositories listing.
     */
    private InstallationRepositories installationRepositories = new InstallationRepositories();

    /**
     * Settings for project-scoped access-request flow before GitHub redirect.
     */
    private AccessRequests accessRequests = new AccessRequests();

    /**
     * Scheduled maintenance jobs for GitHub integration.
     */
    private Jobs jobs = new Jobs();

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

    @Getter
    @Setter
    public static class Jobs {
        /**
         * Repeating cleanup job for expired access-request tokens.
         */
        private AccessRequestCleanup accessRequestCleanup = new AccessRequestCleanup();

        /**
         * Daily cron refresh job for linked GitHub repository state.
         */
        private RepositoryRefresh repositoryRefresh = new RepositoryRefresh();
    }

    @Getter
    @Setter
    public static class AccessRequestCleanup {
        /**
         * Enable/disable expired token cleanup scheduler.
         */
        private boolean enabled = true;

        /**
         * Initial delay before first cleanup run (milliseconds).
         */
        private long initialDelayMs = 120_000L;

        /**
         * Fixed delay between cleanup runs (milliseconds).
         */
        private long fixedDelayMs = 900_000L;
    }

    @Getter
    @Setter
    public static class RepositoryRefresh {
        /**
         * Enable/disable daily linked repository refresh scheduler.
         */
        private boolean enabled = true;

        /**
         * Cron expression for refresh time.
         */
        private String cron = "0 0 0 * * *";

        /**
         * Time zone for cron evaluation.
         */
        private String zone = "UTC";

        /**
         * Maximum number of linked repositories refreshed per run.
         */
        private int batchSize = 50;
    }

}
