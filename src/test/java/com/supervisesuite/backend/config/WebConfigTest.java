package com.supervisesuite.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class WebConfigTest {

    @Test
    void corsConfigurationSource_usesConfiguredOriginsAndCredentials() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("http://localhost:5173", "https://app.supervisesuite.com"));

        WebConfig webConfig = new WebConfig(corsProperties);
        CorsConfigurationSource source = webConfig.corsConfigurationSource();

        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/test"));

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:5173", "https://app.supervisesuite.com");
        assertThat(config.getAllowedMethods()).contains("GET", "POST", "PATCH", "DELETE", "OPTIONS");
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getMaxAge()).isEqualTo(3600L);
    }
}
