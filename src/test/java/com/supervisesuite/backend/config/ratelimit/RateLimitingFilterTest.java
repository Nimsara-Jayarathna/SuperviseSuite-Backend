package com.supervisesuite.backend.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.error.ErrorCode;
import com.supervisesuite.backend.config.RateLimitingProperties;
import com.supervisesuite.backend.config.SecurityErrorResponseWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock
    private InMemoryRateLimiterService rateLimiterService;

    @Mock
    private SecurityErrorResponseWriter securityErrorResponseWriter;

    private RateLimitingProperties properties;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitingProperties();
        filter = new RateLimitingFilter(rateLimiterService, properties, securityErrorResponseWriter);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_skipsWhenGlobalRateLimitingDisabled() throws Exception {
        properties.setEnabled(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(rateLimiterService, never()).evaluate(any(), any());
    }

    @Test
    void doFilterInternal_allowsAuthRequestAndAddsHeaders() throws Exception {
        when(rateLimiterService.evaluate(any(), any()))
            .thenReturn(new RateLimitDecision(true, 10, 9, 0, 60));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("9");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("60");
        verify(securityErrorResponseWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void doFilterInternal_blocksAuthRequestWhenLimitExceeded() throws Exception {
        when(rateLimiterService.evaluate(any(), any()))
            .thenReturn(new RateLimitDecision(false, 10, 0, 42, 42));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
        verify(securityErrorResponseWriter).write(
            eq(request),
            eq(response),
            eq(HttpStatus.TOO_MANY_REQUESTS),
            eq(ErrorCode.TOO_MANY_REQUESTS),
            eq("Too many requests. Please try again later.")
        );
    }

    @Test
    void doFilterInternal_usesAuthenticatedSubjectForProtectedRoutes() throws Exception {
        when(rateLimiterService.evaluate(any(), any()))
            .thenReturn(new RateLimitDecision(true, 240, 239, 0, 60));

        TestingAuthenticationToken authentication = new TestingAuthenticationToken("user@example.com", "pw");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/projects");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("240");
        verify(rateLimiterService).evaluate(eq("authenticated:authenticated-default:user@example.com"), any());
    }

    @Test
    void doFilterInternal_bypassesAuthenticatedLimitForAnonymousRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(rateLimiterService, never()).evaluate(any(), any());
    }
}
