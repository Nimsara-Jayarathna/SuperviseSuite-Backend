package com.supervisesuite.backend.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code app.cors.*} configuration properties.
 *
 * <p>Set {@code CORS_ALLOWED_ORIGINS} in the environment (comma-separated for
 * multiple origins). The default of {@code http://localhost:5173} covers local
 * Vite development.
 *
 * <p>Example environment variable:
 * <pre>
 * CORS_ALLOWED_ORIGINS=http://localhost:5173,https://app.supervisesuite.com
 * </pre>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * List of origins permitted to make cross-origin requests.
     * Mapped from the {@code app.cors.allowed-origins} property; supports
     * comma-separated values via the {@code CORS_ALLOWED_ORIGINS} environment variable.
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
}
