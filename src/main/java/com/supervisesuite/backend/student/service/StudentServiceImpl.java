package com.supervisesuite.backend.student.service;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.memberships.entity.ProjectMember;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StudentServiceImpl implements StudentService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;

    StudentServiceImpl(
        UserRepository userRepository,
        ProjectMemberRepository projectMemberRepository,
        ProjectRepository projectRepository
    ) {
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentProjectSummaryDto> getProjects(String authenticatedUserId) {
        User student = resolveStudent(authenticatedUserId);

        List<ProjectMember> memberships = projectMemberRepository
            .findByUserIdAndMemberRoleOrderByCreatedAtDesc(student.getId(), Roles.STUDENT);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Set<UUID> uniqueProjectIds = memberships.stream()
            .map(ProjectMember::getProjectId)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);

        return projectRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(uniqueProjectIds)
            .stream()
            .map(this::toProjectSummary)
            .toList();
    }

    private User resolveStudent(String authenticatedUserId) {
        UUID studentId;
        try {
            studentId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Authentication required.");
        }

        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new UnauthorizedException("Authentication required."));

        if (!Roles.STUDENT.equals(student.getRole())) {
            throw new UnauthorizedException("Authentication required.");
        }

        return student;
    }

    private StudentProjectSummaryDto toProjectSummary(Project project) {
        User supervisor = project.getSupervisor();
        String supervisorName = null;
        if (supervisor != null) {
            String fullName = ((supervisor.getFirstName() == null ? "" : supervisor.getFirstName()) + " "
                + (supervisor.getLastName() == null ? "" : supervisor.getLastName())).trim();
            supervisorName = fullName.isEmpty() ? supervisor.getEmail() : fullName;
        }

        return new StudentProjectSummaryDto(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getBatch(),
            project.getSemester(),
            project.getMilestoneDate(),
            project.getLastActivityAt(),
            project.getProgressPercent(),
            supervisorName
        );
    }
}
