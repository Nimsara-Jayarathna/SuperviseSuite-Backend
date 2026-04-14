package com.supervisesuite.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.supervisesuite.backend.auth.dto.RegisterConfigResponse;
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
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthControllerRegisterConfigUnitTest {

    @Test
    void registerConfig_returnsDomainAndPrefixRestrictionMetadata() {
        RegistrationProperties registrationProperties = new RegistrationProperties();
        registrationProperties.setDomainRestrictionEnabled(true);
        registrationProperties.setStudentEmailDomain("@my.sliit.lk");
        registrationProperties.setSupervisorEmailDomain("@gmail.com");
        registrationProperties.setStudentEmailPrefixRestrictionEnabled(true);
        registrationProperties.setStudentEmailPrefixRegex("^IT(1[5-9]|[2-4][0-9]|50)\\d{6}$");

        AuthController controller = new AuthController(
            mock(AuthService.class),
            mock(CookieService.class),
            mock(TokenService.class),
            mock(RefreshTokenService.class),
            mock(RefreshTokenValidator.class),
            mock(RegistrationService.class),
            registrationProperties,
            new ApiResponseFactory(),
            mock(PasswordResetService.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/register/config");

        ApiResponse<RegisterConfigResponse> body = controller.registerConfig(request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getData()).isNotNull();
        assertThat(body.getData().isDomainRestrictionEnabled()).isTrue();
        assertThat(body.getData().getStudentDomain()).isEqualTo("@my.sliit.lk");
        assertThat(body.getData().getSupervisorDomain()).isEqualTo("@gmail.com");
        assertThat(body.getData().isStudentEmailPrefixRestrictionEnabled()).isTrue();
        assertThat(body.getData().getStudentEmailPrefixRegex()).isEqualTo("^IT(1[5-9]|[2-4][0-9]|50)\\d{6}$");
    }

    @Test
    void registerConfig_disablesPrefixMetadataWhenDomainRestrictionDisabled() {
        RegistrationProperties registrationProperties = new RegistrationProperties();
        registrationProperties.setDomainRestrictionEnabled(false);
        registrationProperties.setStudentEmailDomain("@my.sliit.lk");
        registrationProperties.setSupervisorEmailDomain("@gmail.com");
        registrationProperties.setStudentEmailPrefixRestrictionEnabled(true);
        registrationProperties.setStudentEmailPrefixRegex("^IT(1[5-9]|[2-4][0-9]|50)\\d{6}$");

        AuthController controller = new AuthController(
            mock(AuthService.class),
            mock(CookieService.class),
            mock(TokenService.class),
            mock(RefreshTokenService.class),
            mock(RefreshTokenValidator.class),
            mock(RegistrationService.class),
            registrationProperties,
            new ApiResponseFactory(),
            mock(PasswordResetService.class)
        );

        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/register/config");

        ApiResponse<RegisterConfigResponse> body = controller.registerConfig(request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getData()).isNotNull();
        assertThat(body.getData().isDomainRestrictionEnabled()).isFalse();
        assertThat(body.getData().isStudentEmailPrefixRestrictionEnabled()).isFalse();
        assertThat(body.getData().getStudentEmailPrefixRegex()).isNull();
    }
}
