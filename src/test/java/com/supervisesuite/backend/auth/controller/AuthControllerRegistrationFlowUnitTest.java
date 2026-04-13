package com.supervisesuite.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.auth.dto.LoginResponse;
import com.supervisesuite.backend.auth.dto.RegisterCompleteRequest;
import com.supervisesuite.backend.auth.dto.RegisterVerifyResponse;
import com.supervisesuite.backend.auth.security.CookieService;
import com.supervisesuite.backend.auth.security.TokenService;
import com.supervisesuite.backend.auth.service.AuthService;
import com.supervisesuite.backend.auth.service.PasswordResetService;
import com.supervisesuite.backend.auth.service.RefreshTokenService;
import com.supervisesuite.backend.auth.service.RefreshTokenValidator;
import com.supervisesuite.backend.auth.service.RegistrationService;
import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.config.RegistrationProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerRegistrationFlowUnitTest {

    @Mock
    private RegistrationService registrationService;

    @Mock
    private CookieService cookieService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        registrationService = mock(RegistrationService.class);
        cookieService = mock(CookieService.class);

        controller = new AuthController(
            mock(AuthService.class),
            cookieService,
            mock(TokenService.class),
            mock(RefreshTokenService.class),
            mock(RefreshTokenValidator.class),
            registrationService,
            new RegistrationProperties(),
            new ApiResponseFactory(),
            mock(PasswordResetService.class)
        );
    }

    @Test
    void registerInit_callsServiceAndReturnsSuccessEnvelope() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register/init");
        var payload = new com.supervisesuite.backend.auth.dto.RegisterInitRequest();
        payload.setEmail("it24123456@my.sliit.lk");

        ApiResponse<java.util.Map<String, String>> body = controller.registerInit(payload, request).getBody();

        verify(registrationService).initRegistration("it24123456@my.sliit.lk");
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).containsEntry("message", "OTP sent successfully");
    }

    @Test
    void registerVerify_returnsRegistrationTokenAndRoleMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register/verify");
        var payload = new com.supervisesuite.backend.auth.dto.RegisterVerifyRequest();
        payload.setEmail("it24123456@my.sliit.lk");
        payload.setOtp("123456");

        RegisterVerifyResponse serviceResponse = new RegisterVerifyResponse("token_abc", false, "STUDENT");
        when(registrationService.verifyOtp("it24123456@my.sliit.lk", "123456")).thenReturn(serviceResponse);

        ApiResponse<RegisterVerifyResponse> body = controller.registerVerify(payload, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isNotNull();
        assertThat(body.getData().getRegistrationToken()).isEqualTo("token_abc");
        assertThat(body.getData().isRequiresRoleSelection()).isFalse();
        assertThat(body.getData().getRole()).isEqualTo("STUDENT");
    }

    @Test
    void registerComplete_setsAuthCookiesAndReturnsCreatedEnvelope() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register/complete");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RegisterCompleteRequest payload = new RegisterCompleteRequest();
        payload.setRegistrationToken("token_abc");
        payload.setFname("Nimal");
        payload.setLname("Perera");
        payload.setPassword("Secure@123");
        payload.setName("IT24123456");

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
            UUID.randomUUID(),
            "it24123456@my.sliit.lk",
            "Nimal",
            "Perera",
            "STUDENT"
        );
        LoginResponse serviceResponse = new LoginResponse("access-token", "refresh-token", userInfo);

        when(registrationService.completeRegistration(payload)).thenReturn(serviceResponse);
        when(cookieService.buildAccessTokenCookie("access-token"))
            .thenReturn(ResponseCookie.from(CookieService.ACCESS_TOKEN_COOKIE, "access-token").path("/api").build());
        when(cookieService.buildRefreshTokenCookie("refresh-token"))
            .thenReturn(ResponseCookie.from(CookieService.REFRESH_TOKEN_COOKIE, "refresh-token").path("/api/auth").build());

        ApiResponse<com.supervisesuite.backend.auth.dto.LoginUserResponse> body =
            controller.registerComplete(payload, request, response).getBody();

        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isNotNull();
        assertThat(body.getData().getUser().getEmail()).isEqualTo("it24123456@my.sliit.lk");

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE).get(0)).contains(CookieService.ACCESS_TOKEN_COOKIE);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE).get(1)).contains(CookieService.REFRESH_TOKEN_COOKIE);
    }
}
