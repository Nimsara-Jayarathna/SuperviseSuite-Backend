package com.supervisesuite.backend.config.ratelimit;

import com.supervisesuite.backend.common.error.ErrorCode;
import com.supervisesuite.backend.config.RateLimitingProperties;
import com.supervisesuite.backend.config.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "/api/auth/";

    private final InMemoryRateLimiterService rateLimiterService;
    private final RateLimitingProperties properties;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    public RateLimitingFilter(
        InMemoryRateLimiterService rateLimiterService,
        RateLimitingProperties properties,
        SecurityErrorResponseWriter securityErrorResponseWriter
    ) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.securityErrorResponseWriter = securityErrorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isEnabled() || HttpMethod.OPTIONS.matches(request.getMethod()) || isSystemPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (path.startsWith(AUTH_PREFIX)) {
            applyAuthLimit(request, response, filterChain, path);
            return;
        }

        applyAuthenticatedRouteLimit(request, response, filterChain);
    }

    private void applyAuthLimit(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain,
        String path
    ) throws IOException, ServletException {
        if (!properties.getAuth().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        RateLimitPolicy policy = new RateLimitPolicy(
            "auth-common",
            properties.getAuth().getWindowSeconds(),
            properties.getAuth().getMaxRequests()
        );
        String key = "auth:" + policy.name() + ":ip:" + clientIp;
        RateLimitDecision decision = rateLimiterService.evaluate(key, policy);

        writeRateLimitHeaders(response, decision);

        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            securityErrorResponseWriter.write(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.TOO_MANY_REQUESTS,
                "Too many requests. Please try again later."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void applyAuthenticatedRouteLimit(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws IOException, ServletException {
        if (!properties.getAuthenticated().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitingProperties.Authenticated config = properties.getAuthenticated();
        RateLimitPolicy policy = new RateLimitPolicy(
            "authenticated-default",
            config.getWindowSeconds(),
            config.getMaxRequests()
        );

        String subject = Optional.ofNullable(authentication.getName())
            .filter(v -> !v.isBlank())
            .orElse("ip:" + resolveClientIp(request));

        String key = "authenticated:" + policy.name() + ":" + subject;
        RateLimitDecision decision = rateLimiterService.evaluate(key, policy);

        writeRateLimitHeaders(response, decision);

        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            securityErrorResponseWriter.write(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.TOO_MANY_REQUESTS,
                "Too many requests. Please try again later."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustForwardedFor()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String[] values = forwardedFor.split(",");
                if (values.length > 0 && !values[0].isBlank()) {
                    return values[0].trim();
                }
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isSystemPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/actuator/health".equals(path) || "/error".equals(path);
    }

    private static void writeRateLimitHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetAfterSeconds()));
    }
}
