package com.supervisesuite.backend.projects.integration.github;

import com.supervisesuite.backend.common.error.DomainException;
import com.supervisesuite.backend.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Raised when a GitHub App installation can no longer be used (removed, revoked, or unauthorized).
 */
public class GitHubInstallationDisconnectedException extends DomainException {

    public GitHubInstallationDisconnectedException(String message) {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    public GitHubInstallationDisconnectedException(String message, Throwable cause) {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message, cause);
    }
}
