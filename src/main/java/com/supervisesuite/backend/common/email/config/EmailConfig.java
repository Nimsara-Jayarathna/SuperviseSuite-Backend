package com.supervisesuite.backend.common.email.config;

import brevoApi.TransactionalEmailsApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Brevo Email Service.
 */
@Configuration
public class EmailConfig {

    @Value("${app.brevo.api-key}")
    private String apiKey;

    /**
     * Initializes the Brevo API Client and TransactionalEmailsApi bean.
     */
    @Bean
    public TransactionalEmailsApi transactionalEmailsApi() {
        brevo.ApiClient defaultClient = brevo.Configuration.getDefaultApiClient();
        defaultClient.setApiKey(apiKey);
        return new TransactionalEmailsApi(defaultClient);
    }
}
