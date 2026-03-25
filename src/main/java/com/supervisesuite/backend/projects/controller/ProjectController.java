package com.supervisesuite.backend.projects.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoriesDto;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ApiResponseFactory apiResponseFactory;
    private final RepositoryLinkService repositoryLinkService;

    public ProjectController(ApiResponseFactory apiResponseFactory, RepositoryLinkService repositoryLinkService) {
        this.apiResponseFactory = apiResponseFactory;
        this.repositoryLinkService = repositoryLinkService;
    }

    @GetMapping("/{id}/github-repositories")
    public ResponseEntity<ApiResponse<ProjectGitHubRepositoriesDto>> getProjectGitHubRepositories(
        Authentication authentication,
        @PathVariable("id") String projectId,
        HttpServletRequest request
    ) {
        String authenticatedUserId = authentication == null ? null : authentication.getName();
        if (authenticatedUserId == null || authenticatedUserId.trim().isEmpty()) {
            throw new UnauthorizedException("Authentication required.");
        }

        ProjectGitHubRepositoriesDto data = repositoryLinkService.getProjectRepositories(projectId, authenticatedUserId);
        return apiResponseFactory.ok("Project GitHub repositories loaded.", data, request);
    }
}
