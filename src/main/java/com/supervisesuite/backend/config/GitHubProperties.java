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
     * GitHub App private key content in PEM format.
     * Supports escaped new lines (\\n) from env files.
     */
    private String appPrivateKey;

    /**
     * GitHub webhook secret used for HMAC signature verification.
     */
    private String appWebhookSecret;

    /**
     * Optional frontend URL to redirect after setup callback.
     */
    private String setupRedirectUrl;

    /**
     * Default page size for GitHub-related paginated API responses.
     */
    private int defaultPageSize = 10;

    /**
     * Upper bound for allowed page size in GitHub-related paginated APIs.
     */
    private int maxPageSize = 100;

}
