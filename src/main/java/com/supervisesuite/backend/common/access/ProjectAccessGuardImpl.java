package com.supervisesuite.backend.common.access;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class ProjectAccessGuardImpl implements ProjectAccessGuard {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    ProjectAccessGuardImpl(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    @Override
    public User requireSupervisor(String authenticatedUserId) {
        return requireUserWithRole(authenticatedUserId, Roles.SUPERVISOR);
    }

    @Override
    public User requireStudent(String authenticatedUserId) {
        return requireUserWithRole(authenticatedUserId, Roles.STUDENT);
    }

    @Override
    public Project requireSupervisorOwnsProject(User supervisor, UUID projectId) {
        if (supervisor == null || projectId == null) {
            throw new EntityNotFoundException();
        }
        return projectRepository
                .findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, supervisor.getId())
                .orElseThrow(EntityNotFoundException::new);
    }

    @Override
    public Project requireStudentIsMember(User student, UUID projectId) {
        if (student == null || projectId == null) {
            throw new EntityNotFoundException();
        }
        boolean hasMembership = projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(
                student.getId(),
                projectId,
                Roles.STUDENT);
        if (!hasMembership) {
            throw new EntityNotFoundException();
        }

        return projectRepository
                .findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(EntityNotFoundException::new);
    }

    private User requireUserWithRole(String authenticatedUserId, String role) {
        UUID userId;
        try {
            userId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Authentication required.");
        }

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authentication required."));

        if (!role.equals(user.getRole())) {
            throw new UnauthorizedException("Authentication required.");
        }

        return user;
    }
}

