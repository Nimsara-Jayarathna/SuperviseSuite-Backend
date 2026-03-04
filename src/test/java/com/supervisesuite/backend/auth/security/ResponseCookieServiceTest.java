package com.supervisesuite.backend.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.config.JwtProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/**
 * Unit tests for {@link ResponseCookieService}.
 *
 * <p>No Spring context is loaded — {@link JwtProperties} is constructed manually
 * and {@link ResponseCookieService} is instantiated directly. Every assertion covers
 * one of the three axes that matter for security:
 * <ol>
 *   <li><b>Identity</b> — correct cookie name and value.</li>
 *   <li><b>Scope</b> — path and max-age match the configured TTL (or 0 for clear cookies).</li>
 *   <li><b>Security flags</b> — {@code HttpOnly}, {@code Secure}, {@code SameSite=Strict}
 *       are present on every cookie variant.</li>
 * </ol>
 */
class ResponseCookieServiceTest {

    private static final long ACCESS_TTL = 900L;    // 15 minutes
    private static final long REFRESH_TTL = 604800L; // 7 days
    private static final String TOKEN_VALUE = "some.jwt.token";

    private ResponseCookieService cookieService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("does-not-matter-for-cookie-tests");
        props.setAccessTokenExpirySeconds(ACCESS_TTL);
        props.setRefreshTokenExpirySeconds(REFRESH_TTL);

        cookieService = new ResponseCookieService(props);
    }

    // -------------------------------------------------------------------------
    // buildAccessTokenCookie
    // -------------------------------------------------------------------------

    @Nested
    class BuildAccessTokenCookie {

        @Test
        void hasCorrectName() {
            ResponseCookie cookie = cookieService.buildAccessTokenCookie(TOKEN_VALUE);
            assertThat(cookie.getName()).isEqualTo(CookieService.ACCESS_TOKEN_COOKIE);
        }

        @Test
        void hasCorrectValue() {
            ResponseCookie cookie = cookieService.buildAccessTokenCookie(TOKEN_VALUE);
            assertThat(cookie.getValue()).isEqualTo(TOKEN_VALUE);
        }

        @Test
        void pathIsApi() {
            ResponseCookie cookie = cookieService.buildAccessTokenCookie(TOKEN_VALUE);
            assertThat(cookie.getPath()).isEqualTo("/api");
        }

        @Test
        void maxAgeMatchesConfig() {
            ResponseCookie cookie = cookieService.buildAccessTokenCookie(TOKEN_VALUE);
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(ACCESS_TTL));
        }

        @Test
        void isHttpOnly() {
            assertThat(cookieService.buildAccessTokenCookie(TOKEN_VALUE).isHttpOnly()).isTrue();
        }

        @Test
        void isSecure() {
            assertThat(cookieService.buildAccessTokenCookie(TOKEN_VALUE).isSecure()).isTrue();
        }

        @Test
        void sameSiteIsStrict() {
            assertThat(cookieService.buildAccessTokenCookie(TOKEN_VALUE).getSameSite())
                .isEqualTo("Strict");
        }
    }

    // -------------------------------------------------------------------------
    // buildRefreshTokenCookie
    // -------------------------------------------------------------------------

    @Nested
    class BuildRefreshTokenCookie {

        @Test
        void hasCorrectName() {
            ResponseCookie cookie = cookieService.buildRefreshTokenCookie(TOKEN_VALUE);
            assertThat(cookie.getName()).isEqualTo(CookieService.REFRESH_TOKEN_COOKIE);
        }

        @Test
        void hasCorrectValue() {
            ResponseCookie cookie = cookieService.buildRefreshTokenCookie(TOKEN_VALUE);
            assertThat(cookie.getValue()).isEqualTo(TOKEN_VALUE);
        }

        @Test
        void pathIsApiAuth() {
            ResponseCookie cookie = cookieService.buildRefreshTokenCookie(TOKEN_VALUE);
            assertThat(cookie.getPath()).isEqualTo("/api/auth");
        }

        @Test
        void maxAgeMatchesConfig() {
            ResponseCookie cookie = cookieService.buildRefreshTokenCookie(TOKEN_VALUE);
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(REFRESH_TTL));
        }

        @Test
        void isHttpOnly() {
            assertThat(cookieService.buildRefreshTokenCookie(TOKEN_VALUE).isHttpOnly()).isTrue();
        }

        @Test
        void isSecure() {
            assertThat(cookieService.buildRefreshTokenCookie(TOKEN_VALUE).isSecure()).isTrue();
        }

        @Test
        void sameSiteIsStrict() {
            assertThat(cookieService.buildRefreshTokenCookie(TOKEN_VALUE).getSameSite())
                .isEqualTo("Strict");
        }
    }

    // -------------------------------------------------------------------------
    // buildClearAccessTokenCookie
    // -------------------------------------------------------------------------

    @Nested
    class BuildClearAccessTokenCookie {

        @Test
        void hasCorrectName() {
            ResponseCookie cookie = cookieService.buildClearAccessTokenCookie();
            assertThat(cookie.getName()).isEqualTo(CookieService.ACCESS_TOKEN_COOKIE);
        }

        @Test
        void valueIsEmpty() {
            ResponseCookie cookie = cookieService.buildClearAccessTokenCookie();
            assertThat(cookie.getValue()).isEmpty();
        }

        @Test
        void pathIsApi() {
            ResponseCookie cookie = cookieService.buildClearAccessTokenCookie();
            assertThat(cookie.getPath()).isEqualTo("/api");
        }

        @Test
        void maxAgeIsZero() {
            ResponseCookie cookie = cookieService.buildClearAccessTokenCookie();
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        }

        @Test
        void isHttpOnly() {
            assertThat(cookieService.buildClearAccessTokenCookie().isHttpOnly()).isTrue();
        }

        @Test
        void isSecure() {
            assertThat(cookieService.buildClearAccessTokenCookie().isSecure()).isTrue();
        }

        @Test
        void sameSiteIsStrict() {
            assertThat(cookieService.buildClearAccessTokenCookie().getSameSite())
                .isEqualTo("Strict");
        }
    }

    // -------------------------------------------------------------------------
    // buildClearRefreshTokenCookie
    // -------------------------------------------------------------------------

    @Nested
    class BuildClearRefreshTokenCookie {

        @Test
        void hasCorrectName() {
            ResponseCookie cookie = cookieService.buildClearRefreshTokenCookie();
            assertThat(cookie.getName()).isEqualTo(CookieService.REFRESH_TOKEN_COOKIE);
        }

        @Test
        void valueIsEmpty() {
            ResponseCookie cookie = cookieService.buildClearRefreshTokenCookie();
            assertThat(cookie.getValue()).isEmpty();
        }

        @Test
        void pathIsApiAuth() {
            ResponseCookie cookie = cookieService.buildClearRefreshTokenCookie();
            assertThat(cookie.getPath()).isEqualTo("/api/auth");
        }

        @Test
        void maxAgeIsZero() {
            ResponseCookie cookie = cookieService.buildClearRefreshTokenCookie();
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        }

        @Test
        void isHttpOnly() {
            assertThat(cookieService.buildClearRefreshTokenCookie().isHttpOnly()).isTrue();
        }

        @Test
        void isSecure() {
            assertThat(cookieService.buildClearRefreshTokenCookie().isSecure()).isTrue();
        }

        @Test
        void sameSiteIsStrict() {
            assertThat(cookieService.buildClearRefreshTokenCookie().getSameSite())
                .isEqualTo("Strict");
        }
    }
}
