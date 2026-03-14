package com.supervisesuite.backend.config;

import com.supervisesuite.backend.auth.security.JwtAuthFilter;
import com.supervisesuite.backend.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                // Missing or invalid token on a protected endpoint -> 401
                // Without this, Spring's default sends an HTML redirect to /login
                // which is wrong for a stateless REST API.
                .authenticationEntryPoint((request, response, authException) ->
                    securityErrorResponseWriter.write(
                        request,
                        response,
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        ErrorCode.UNAUTHORIZED,
                        "Authentication required."
                    )
                )
                // Valid token but insufficient role -> 403
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    securityErrorResponseWriter.write(
                        request,
                        response,
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        ErrorCode.FORBIDDEN,
                        "Access denied."
                    )
                )
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
