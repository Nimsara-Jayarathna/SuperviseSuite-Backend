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
 * <p>Runs once per request (extends {@link OncePerRequestFilter}). Extracts the
 * access token from the incoming request via {@link CookieTokenExtractor}, validates
 * it via {@link TokenService}, and populates the {@link SecurityContextHolder} with
 * the authenticated principal.
 *
 * <p>If the cookie is absent, the token is invalid/expired, or any claim cannot be
 * extracted, the filter proceeds without setting authentication. Spring Security will
 * then reject the request based on the route's authorization rule (401/403).
 *
 * <p>Role claim is read directly from the JWT — no DB call is made per request.
 * Authorities are set as {@code ROLE_STUDENT} or {@code ROLE_SUPERVISOR} so that
 * Spring Security's {@code hasRole("STUDENT")} / {@code hasRole("SUPERVISOR")} works correctly.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final CookieTokenExtractor tokenExtractor;

    public JwtAuthFilter(TokenService tokenService, CookieTokenExtractor tokenExtractor) {
        this.tokenService = tokenService;
        this.tokenExtractor = tokenExtractor;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        // No access token cookie — skip, let Spring Security decide
        String token = tokenExtractor.extractAccessToken(request).orElse(null);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

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
