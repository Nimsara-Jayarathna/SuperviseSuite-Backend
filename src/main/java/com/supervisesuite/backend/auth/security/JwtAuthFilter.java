package com.supervisesuite.backend.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless JWT authentication filter.
 *
 * <p>Runs once per request (extends {@link OncePerRequestFilter}). Extracts the Bearer
 * token from the {@code Authorization} header, validates it via {@link JwtService},
 * and populates the {@link SecurityContextHolder} with the authenticated principal.
 *
 * <p>If the header is absent, the token is invalid/expired, or any field cannot be
 * extracted, the filter proceeds without setting authentication. Spring Security will
 * then reject the request based on the route's authorization rule (401/403).
 *
 * <p>Role claim is read directly from the JWT — no DB call is made per request.
 * Authorities are set as {@code ROLE_STUDENT} or {@code ROLE_SUPERVISOR} so that
 * Spring Security's {@code hasRole("STUDENT")} / {@code hasRole("SUPERVISOR")} works correctly.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public JwtAuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No Authorization header or wrong scheme — skip, let Spring Security decide
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // Extract subject (userId) and role from JWT claims — both must be present
        String userId = tokenService.extractSubject(token).orElse(null);
        String role   = tokenService.extractRole(token).orElse(null);

        if (userId == null || role == null) {
            // Token is expired, malformed, or tampered — proceed unauthenticated
            filterChain.doFilter(request, response);
            return;
        }

        // Only set authentication if the context is not already populated
        // (avoids overwriting auth set by an earlier filter in the chain)
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            var authority = new SimpleGrantedAuthority("ROLE_" + role);
            var authentication = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(authority)
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
