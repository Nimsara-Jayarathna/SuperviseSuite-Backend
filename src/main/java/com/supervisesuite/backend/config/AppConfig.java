package com.supervisesuite.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(RegistrationProperties.class)
public class AppConfig {

    /**
     * BCrypt password encoder used for hashing and verifying user passwords.
     * Strength defaults to 10 (BCrypt standard). Shared as a Spring bean
     * so both AuthService (encoding) and Spring Security (verification) use
     * the same instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
