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
     * Default page size for GitHub-related paginated API responses.
     */
    private int defaultPageSize = 10;

    /**
     * Upper bound for allowed page size in GitHub-related paginated APIs.
     */
    private int maxPageSize = 100;

}
