package com.supervisesuite.backend.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Cookie-based implementation of {@link CookieTokenExtractor}.
 *
 * <p>Locates the access token by scanning the request's cookie array for a
 * cookie whose name equals {@link CookieService#ACCESS_TOKEN_COOKIE}. Returns
 * its value if found, or {@link Optional#empty()} if the cookie is absent or
 * the request carries no cookies at all.
 *
 * <p>Package-private — callers depend on {@link CookieTokenExtractor} only.
 */
@Component
class CookieTokenExtractorImpl implements CookieTokenExtractor {

    @Override
    public Optional<String> extractAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(c -> CookieService.ACCESS_TOKEN_COOKIE.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst();
    }
}
