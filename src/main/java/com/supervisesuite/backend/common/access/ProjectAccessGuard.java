package com.supervisesuite.backend.common.access;

import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.users.entity.User;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;

/**
 * Centralized access checks for project ownership and membership.
 *
 * <p>Purposefully throws {@link EntityNotFoundException} for "not owned / not a member"
 * to avoid leaking resource existence details.
 */
public interface ProjectAccessGuard {

    User requireSupervisor(String authenticatedUserId);

    User requireStudent(String authenticatedUserId);

    Project requireSupervisorOwnsProject(User supervisor, UUID projectId) throws EntityNotFoundException;

    Project requireStudentIsMember(User student, UUID projectId) throws EntityNotFoundException;
}

