package com.supervisesuite.backend.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Web MVC configuration.
 *
 * <p>Provides the {@link CorsConfigurationSource} bean consumed by
 * {@link SecurityConfig} via {@code .cors(Customizer.withDefaults())}.
 * Allowed origins are driven by {@link CorsProperties}, which reads from
 * the {@code CORS_ALLOWED_ORIGINS} environment variable so no code change
 * is needed between environments.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig {

    private final CorsProperties corsProperties;

    /**
     * Registers the CORS policy applied by the Spring Security filter chain.
     *
     * <ul>
     *   <li><b>Allowed origins</b> — configured via {@code CORS_ALLOWED_ORIGINS};
     *       defaults to {@code http://localhost:5173} (local Vite dev server).</li>
     *   <li><b>Allowed methods</b> — GET, POST, PUT, PATCH, DELETE, OPTIONS.</li>
     *   <li><b>Allowed headers</b> — all headers permitted ({@code *}); avoids
     *       CORS rejections from browsers and custom API clients.</li>
     *   <li><b>Credentials</b> — {@code false}; JWT is sent as a Bearer token in
     *       the {@code Authorization} header, not as a cookie.</li>
     *   <li><b>Preflight cache</b> — 3600 seconds (1 hour).</li>
     * </ul>
     *
     * @return a {@link CorsConfigurationSource} mapped to all paths
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.addAllowedHeader("*");
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
