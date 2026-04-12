package com.supervisesuite.backend.auth.controller;

import com.supervisesuite.backend.auth.dto.LoginRequest;
import com.supervisesuite.backend.auth.dto.LoginResponse;
import com.supervisesuite.backend.auth.dto.LoginUserResponse;
import com.supervisesuite.backend.auth.dto.RegisterConfigResponse;
import com.supervisesuite.backend.auth.dto.RegisterCompleteRequest;
import com.supervisesuite.backend.auth.dto.RegisterInitRequest;
import com.supervisesuite.backend.auth.dto.RegisterVerifyRequest;
import com.supervisesuite.backend.auth.dto.RegisterVerifyResponse;
import com.supervisesuite.backend.auth.security.CookieService;
import com.supervisesuite.backend.auth.security.TokenService;
import com.supervisesuite.backend.auth.service.AuthService;
import com.supervisesuite.backend.auth.service.RefreshTokenService;
import com.supervisesuite.backend.auth.service.RefreshTokenValidator;
import com.supervisesuite.backend.auth.service.RegistrationService;
import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.config.RegistrationProperties;
import com.supervisesuite.backend.users.entity.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 *
 * <p>All routes under {@code /api/auth} are publicly accessible
 * (no JWT required) as configured in
 * {@link com.supervisesuite.backend.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenValidator refreshTokenValidator;
    private final RegistrationService registrationService;
    private final RegistrationProperties registrationProperties;
    private final ApiResponseFactory apiResponseFactory;

    public AuthController(
        AuthService authService,
        CookieService cookieService,
        TokenService tokenService,
        RefreshTokenService refreshTokenService,
        RefreshTokenValidator refreshTokenValidator,
        RegistrationService registrationService,
        RegistrationProperties registrationProperties,
        ApiResponseFactory apiResponseFactory
    ) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenValidator = refreshTokenValidator;
        this.registrationService = registrationService;
        this.registrationProperties = registrationProperties;
        this.apiResponseFactory = apiResponseFactory;
    }

    @GetMapping("/register/config")
    public ResponseEntity<ApiResponse<RegisterConfigResponse>> registerConfig(
        HttpServletRequest httpRequest
    ) {
        RegisterConfigResponse data = new RegisterConfigResponse(
            registrationProperties.isDomainRestrictionEnabled(),
            registrationProperties.hasStudentDomain()
                ? registrationProperties.getStudentEmailDomain() : null,
            registrationProperties.hasSupervisorDomain()
                ? registrationProperties.getSupervisorEmailDomain() : null,
            registrationProperties.isEffectiveStudentEmailPrefixRestrictionEnabled(),
            registrationProperties.isEffectiveStudentEmailPrefixRestrictionEnabled()
                ? registrationProperties.getStudentEmailPrefixRegex() : null
        );
        return apiResponseFactory.ok("Registration configuration", data, httpRequest);
    }

    @PostMapping("/register/init")
    public ResponseEntity<ApiResponse<Map<String, String>>> registerInit(
        @Valid @RequestBody RegisterInitRequest request,
        HttpServletRequest httpRequest
    ) {
        registrationService.initRegistration(request.getEmail());
        return apiResponseFactory.ok(
            "Registration initiated",
            Map.of("message", "OTP sent successfully"),
            httpRequest
        );
    }

    @PostMapping("/register/verify")
    public ResponseEntity<ApiResponse<RegisterVerifyResponse>> registerVerify(
        @Valid @RequestBody RegisterVerifyRequest request,
        HttpServletRequest httpRequest
    ) {
        RegisterVerifyResponse data = registrationService.verifyOtp(request.getEmail(), request.getOtp());
        return apiResponseFactory.ok("OTP verified", data, httpRequest);
    }

    @PostMapping("/register/complete")
    public ResponseEntity<ApiResponse<LoginUserResponse>> registerComplete(
        @Valid @RequestBody RegisterCompleteRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        LoginResponse data = registrationService.completeRegistration(request);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
            cookieService.buildAccessTokenCookie(data.getAccessToken()).toString());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
            cookieService.buildRefreshTokenCookie(data.getRefreshToken()).toString());
        return apiResponseFactory.created(
            "Authentication successful",
            new LoginUserResponse(data.getUser()),
            httpRequest
        );
    }

    /**
     * Authenticates a user and issues access and refresh tokens.
     *
     * <pre>
     * POST /api/auth/login
     * </pre>
     *
     * <p>Request body is validated via Bean Validation. Any constraint violation
     * produces a {@code 400 VALIDATION_ERROR} response before the service layer is reached.
     *
     * <p>Invalid credentials always return {@code 401} with a generic message —
     * the response deliberately does not distinguish between an unknown email
     * and a wrong password to prevent user enumeration.
     *
     * @param request the login payload containing email and password
     * @param httpResponse the servlet response used to attach httpOnly cookies
     * @return {@code 200 OK} with an {@link ApiResponse} wrapping a {@link LoginUserResponse}
     *         containing the authenticated user's public profile; tokens are delivered
     *         via {@code HttpOnly; Secure; SameSite=Strict} {@code Set-Cookie} headers
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginUserResponse>> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        LoginResponse data = authService.login(request);

        // Deliver tokens as httpOnly cookies — never in the response body
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
            cookieService.buildAccessTokenCookie(data.getAccessToken()).toString());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
            cookieService.buildRefreshTokenCookie(data.getRefreshToken()).toString());

        return apiResponseFactory.ok("Login successful.", new LoginUserResponse(data.getUser()), httpRequest);
    }

    /**
     * Issues a new access token and rotates the refresh token.
     *
     * <pre>
     * POST /api/auth/refresh
     * </pre>
     *
     * <p>Reads the refresh token from the {@code ss_refresh_token} httpOnly cookie.
     * The old refresh token is revoked immediately after validation (token rotation)
     * so each refresh token can only be used once.
     *
     * @param httpRequest  the incoming request carrying the refresh token cookie
     * @param httpResponse the response on which the new cookies are set
     * @return {@code 200 OK} with the authenticated user's public profile;
     *         {@code 401} if the cookie is absent, the token is unknown, expired,
     *         or has already been revoked
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginUserResponse>> refresh(
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        String rawRefreshToken = extractCookie(httpRequest, CookieService.REFRESH_TOKEN_COOKIE)
            .orElseThrow(() -> new UnauthorizedException("Refresh token is missing."));

        // Validate — throws UnauthorizedException if unknown, revoked, or expired
        User user = refreshTokenValidator.validate(rawRefreshToken);

        // Rotate: revoke the consumed token, issue a fresh one
        refreshTokenService.revoke(rawRefreshToken);
        String newRawRefreshToken = refreshTokenService.issue(user);
        String newAccessToken = tokenService.generateAccessToken(user);

        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
            cookieService.buildAccessTokenCookie(newAccessToken).toString());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
            cookieService.buildRefreshTokenCookie(newRawRefreshToken).toString());

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
            user.getId(), user.getEmail(), user.getFirstName(),
            user.getLastName(), user.getRole()
        );

        return apiResponseFactory.ok("Token refreshed.", new LoginUserResponse(userInfo), httpRequest);
    }

    /**
     * Revokes the caller's refresh token and clears both auth cookies.
     *
     * <pre>
     * POST /api/auth/logout
     * </pre>
     *
     * <p>This endpoint is intentionally idempotent and graceful:
     * <ul>
     *   <li>If the {@code ss_refresh_token} cookie is present, the token is
     *       revoked so it cannot be used for future refresh calls.</li>
     *   <li>If the cookie is absent or its value is unknown, the call still
     *       succeeds — the client can always trust that after this response its
     *       cookies have been cleared.</li>
     * </ul>
     *
     * <p>The response always sets {@code Max-Age=0} on both auth cookies,
     * instructing the browser to delete them immediately regardless of the
     * token's validity.
     *
     * @param httpRequest  the incoming request, examined for a refresh token cookie
     * @param httpResponse the response on which the clear-cookie headers are set
     * @return {@code 204 No Content}
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        // Silently revoke the refresh token if one was sent — no error if absent/unknown
        extractCookie(httpRequest, CookieService.REFRESH_TOKEN_COOKIE)
            .ifPresent(refreshTokenService::revoke);

        // Always clear both cookies so the browser drops them
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
            cookieService.buildClearAccessTokenCookie().toString());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
            cookieService.buildClearRefreshTokenCookie().toString());

        return ResponseEntity.noContent().build();
    }

    /**
     * Reads a single cookie value from the request by name.
     *
     * @param request    the current HTTP servlet request
     * @param cookieName the exact cookie name to find
     * @return an {@link Optional} containing the cookie value, or empty if absent
     */
    private static Optional<String> extractCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(c -> cookieName.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst();
    }
}
