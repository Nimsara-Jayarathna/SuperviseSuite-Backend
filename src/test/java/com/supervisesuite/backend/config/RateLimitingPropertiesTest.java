package com.supervisesuite.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitingPropertiesTest {

    @Test
    void defaults_matchExpectedRateLimitingContract() {
        RateLimitingProperties properties = new RateLimitingProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isTrustForwardedFor()).isTrue();

        assertThat(properties.getAuth().isEnabled()).isTrue();
        assertThat(properties.getAuth().getWindowSeconds()).isEqualTo(60);
        assertThat(properties.getAuth().getMaxRequests()).isEqualTo(10);

        assertThat(properties.getAuthenticated().isEnabled()).isTrue();
        assertThat(properties.getAuthenticated().getWindowSeconds()).isEqualTo(60);
        assertThat(properties.getAuthenticated().getMaxRequests()).isEqualTo(240);
    }
}
