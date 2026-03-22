package com.supervisesuite.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AppConfigTest {

    @Test
    void passwordEncoder_returnsBCryptEncoder() {
        AppConfig config = new AppConfig();

        PasswordEncoder encoder = config.passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(encoder.matches("Secure@123", encoder.encode("Secure@123"))).isTrue();
    }
}
