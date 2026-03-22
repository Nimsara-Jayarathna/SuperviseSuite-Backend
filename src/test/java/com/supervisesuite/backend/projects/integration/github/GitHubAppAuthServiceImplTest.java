package com.supervisesuite.backend.projects.integration.github;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supervisesuite.backend.common.error.ApiErrorDetail;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GitHubAppAuthServiceImplTest {

    private GitHubProperties properties;
    private GitHubAppAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties();
        properties.setApiBaseUrl("https://api.github.com");
        service = new GitHubAppAuthServiceImpl(properties, RestClient.builder());
    }

    @Test
    void createAppJwt_missingAppId_throwsValidationException() {
        properties.setAppPrivateKey("-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----");

        assertThatThrownBy(() -> service.createAppJwt())
            .isInstanceOfSatisfying(ValidationException.class, exception ->
                assertHasDetail(exception, "GITHUB_APP_ID", "is not configured")
            );
    }

    @Test
    void createAppJwt_missingPrivateKey_throwsValidationException() {
        properties.setAppId("123456");

        assertThatThrownBy(() -> service.createAppJwt())
            .isInstanceOfSatisfying(ValidationException.class, exception ->
                assertHasDetail(exception, "GITHUB_APP_PRIVATE_KEY", "is not configured")
            );
    }

    @Test
    void createAppJwt_invalidPrivateKeyFormat_throwsValidationException() {
        properties.setAppId("123456");
        properties.setAppPrivateKey("not-a-pem-and-not-a-path");

        assertThatThrownBy(() -> service.createAppJwt())
            .isInstanceOfSatisfying(ValidationException.class, exception ->
                assertHasDetail(exception, "GITHUB_APP_PRIVATE_KEY", "Provide PEM key content")
            );
    }

    @Test
    void createInstallationAccessToken_invalidInstallationId_throwsValidationException() {
        assertThatThrownBy(() -> service.createInstallationAccessToken(0L))
            .isInstanceOfSatisfying(ValidationException.class, exception ->
                assertHasDetail(exception, "installationId", "Installation id is required")
            );
    }

    @Test
    void fetchInstallationContext_invalidInstallationId_throwsValidationException() {
        assertThatThrownBy(() -> service.fetchInstallationContext(null))
            .isInstanceOfSatisfying(ValidationException.class, exception ->
                assertHasDetail(exception, "installationId", "Installation id is required")
            );
    }

    @Test
    void fetchInstallationRepositories_invalidPageOrSize_throwsValidationException() {
        assertThatThrownBy(() -> service.fetchInstallationRepositories(10L, 0, 10))
            .isInstanceOfSatisfying(ValidationException.class, exception ->
                assertHasDetail(exception, "page", "Page must be greater than zero")
            );

        assertThatThrownBy(() -> service.fetchInstallationRepositories(10L, 1, 0))
            .isInstanceOfSatisfying(ValidationException.class, exception ->
                assertHasDetail(exception, "size", "Size must be greater than zero")
            );
    }

    private static void assertHasDetail(ValidationException exception, String field, String issueFragment) {
        org.assertj.core.api.Assertions.assertThat(exception.getDetails())
            .anySatisfy(detail -> {
                org.assertj.core.api.Assertions.assertThat(detail).isInstanceOf(ApiErrorDetail.class);
                org.assertj.core.api.Assertions.assertThat(detail.getField()).isEqualTo(field);
                org.assertj.core.api.Assertions.assertThat(detail.getIssue()).contains(issueFragment);
            });
    }
}
