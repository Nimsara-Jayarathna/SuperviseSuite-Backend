package com.supervisesuite.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Base64-encoded HMAC-SHA256 secret. Must be at least 64 bytes (512 bits). */
    private String secret;

    /** Access token lifetime in seconds. Default: 900 (15 minutes). */
    private long accessTokenExpirySeconds;

    /** Refresh token lifetime in seconds. Default: 604800 (7 days). */
    private long refreshTokenExpirySeconds;
}
