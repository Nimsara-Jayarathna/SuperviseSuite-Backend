package com.supervisesuite.backend.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CookieTokenExtractorImpl}.
 *
 * <p>No Spring context — {@link HttpServletRequest} is mocked with Mockito.
 */
class CookieTokenExtractorImplTest {

    private CookieTokenExtractorImpl extractor;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        extractor = new CookieTokenExtractorImpl();
        request = mock(HttpServletRequest.class);
    }

    // -------------------------------------------------------------------------
    // No-cookie cases
    // -------------------------------------------------------------------------

    @Test
    void returnsEmpty_whenRequestHasNoCookies() {
        when(request.getCookies()).thenReturn(null);

        assertThat(extractor.extractAccessToken(request)).isEmpty();
    }

    @Test
    void returnsEmpty_whenCookieArrayIsEmpty() {
        when(request.getCookies()).thenReturn(new Cookie[0]);

        assertThat(extractor.extractAccessToken(request)).isEmpty();
    }

    @Test
    void returnsEmpty_whenAccessTokenCookieIsAbsent() {
        when(request.getCookies()).thenReturn(new Cookie[]{
            new Cookie("some_other_cookie", "irrelevant")
        });

        assertThat(extractor.extractAccessToken(request)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Present cases
    // -------------------------------------------------------------------------

    @Test
    void returnsValue_whenAccessTokenCookieIsPresent() {
        String tokenValue = "header.payload.signature";
        when(request.getCookies()).thenReturn(new Cookie[]{
            new Cookie(CookieService.ACCESS_TOKEN_COOKIE, tokenValue)
        });

        Optional<String> result = extractor.extractAccessToken(request);

        assertThat(result).contains(tokenValue);
    }

    @Test
    void returnsCorrectValue_whenMultipleCookiesPresent() {
        String tokenValue = "correct.jwt.token";
        when(request.getCookies()).thenReturn(new Cookie[]{
            new Cookie("session_id", "abc123"),
            new Cookie(CookieService.ACCESS_TOKEN_COOKIE, tokenValue),
            new Cookie(CookieService.REFRESH_TOKEN_COOKIE, "refresh.token.value")
        });

        Optional<String> result = extractor.extractAccessToken(request);

        assertThat(result).contains(tokenValue);
    }

    @Test
    void doesNotReturnRefreshToken_whenOnlyRefreshCookiePresent() {
        when(request.getCookies()).thenReturn(new Cookie[]{
            new Cookie(CookieService.REFRESH_TOKEN_COOKIE, "refresh.token.value")
        });

        assertThat(extractor.extractAccessToken(request)).isEmpty();
    }

    @Test
    void doesNotMatchCookieNameCaseInsensitively() {
        // Cookie name matching must be exact — no case-folding
        when(request.getCookies()).thenReturn(new Cookie[]{
            new Cookie(CookieService.ACCESS_TOKEN_COOKIE.toUpperCase(), "some-value")
        });

        assertThat(extractor.extractAccessToken(request)).isEmpty();
    }
}
