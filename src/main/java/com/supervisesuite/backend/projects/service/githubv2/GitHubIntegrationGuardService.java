package com.supervisesuite.backend.projects.service.githubv2;

import com.supervisesuite.backend.common.error.DomainException;
import com.supervisesuite.backend.common.error.ErrorCode;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GitHubIntegrationGuardService {

    private final ProjectRepository projectRepository;

    public GitHubIntegrationGuardService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project requireOwnedProject(String projectIdRaw, String authenticatedUserIdRaw) {
        UUID projectId = parseUuid(projectIdRaw, "projectId");
        UUID authenticatedUserId = parseUuid(authenticatedUserIdRaw, "authenticatedUserId");
        return requireOwnedProject(projectId, authenticatedUserId);
    }

    public Project requireOwnedProject(UUID projectId, UUID authenticatedUserId) {
        if (authenticatedUserId == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        return projectRepository
            .findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, authenticatedUserId)
            .orElseThrow(() -> new DomainException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Access denied."));
    }

    public Project requireExistingProject(UUID projectId) {
        return projectRepository
            .findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new ValidationException("projectId", "Project not found."));
    }

    public UUID parseUuid(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(field, field + " is required.");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(field, field + " must be a valid UUID.");
        }
    }
}
