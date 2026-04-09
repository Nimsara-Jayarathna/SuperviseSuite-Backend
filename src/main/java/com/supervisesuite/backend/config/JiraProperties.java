package com.supervisesuite.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jira")
public class JiraProperties {
    private String clientId;
    private String clientSecret;
    private String scope = "read:jira-user read:jira-work";
    private String audience = "api.atlassian.com";
    private String authTargetUrl = "https://auth.atlassian.com/authorize";
    private String tokenTargetUrl = "https://auth.atlassian.com/oauth/token";
    private String redirectUri;
    private String oauthState = "supervisesuite_jira_state";
    private long oauthStateTtlSeconds = 900;
    private String tokenEncryptionSecret;
    private Analytics analytics = new Analytics();

    @Getter
    @Setter
    public static class Analytics {
        private int recentSprintsLimit = 3;
        private int backlogGrowingConsecutiveWeeks = 2;
        private List<String> highPriorityNames = new ArrayList<>(List.of("High", "Highest"));
        private List<String> bugTypeNames = new ArrayList<>(List.of("Bug"));
    }
}
