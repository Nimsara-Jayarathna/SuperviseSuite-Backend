package com.supervisesuite.backend.projects.service.githubv2;

import com.supervisesuite.backend.projects.dto.GitHubWebhookResultDto;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

    private final GitHubAppIntegrationService gitHubAppIntegrationService;

    public WebhookService(GitHubAppIntegrationService gitHubAppIntegrationService) {
        this.gitHubAppIntegrationService = gitHubAppIntegrationService;
    }

    @Transactional
    public GitHubWebhookResultDto handleWebhook(String event, String signature256, String payload) {
        return gitHubAppIntegrationService.handleWebhook(event, signature256, payload);
    }
}
