package com.supervisesuite.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}
