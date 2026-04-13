package com.supervisesuite.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.rate-limiting")
public class RateLimitingProperties {

    private boolean enabled = true;
    private boolean trustForwardedFor = true;
    private Auth auth = new Auth();
    private Authenticated authenticated = new Authenticated();

    @Getter
    @Setter
    public static class Auth {
        private boolean enabled = true;
        private int windowSeconds = 60;
        private int maxRequests = 10;
    }

    @Getter
    @Setter
    public static class Authenticated {
        private boolean enabled = true;
        private int windowSeconds = 60;
        private int maxRequests = 240;
    }
}
