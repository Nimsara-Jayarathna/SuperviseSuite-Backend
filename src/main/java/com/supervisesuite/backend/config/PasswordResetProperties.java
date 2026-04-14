package com.supervisesuite.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth.password-reset")
public class PasswordResetProperties {
    private int tokenExpiryMinutes = 15;
    private String cleanupCron = "0 0 * * * *";
}
