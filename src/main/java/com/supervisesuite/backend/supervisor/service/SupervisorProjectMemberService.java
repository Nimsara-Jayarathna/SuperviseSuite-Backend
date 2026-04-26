package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.EntityIdParser;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.memberships.entity.ProjectMember;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMembersRequest;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupervisorProjectMemberService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessGuard projectAccessGuard;
    private final SupervisorProjectDtoMapper projectDtoMapper;

    SupervisorProjectMemberService(
            UserRepository userRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectRepository projectRepository,
            ProjectAccessGuard projectAccessGuard,
            SupervisorProjectDtoMapper projectDtoMapper) {
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectRepository = projectRepository;
        this.projectAccessGuard = projectAccessGuard;
        this.projectDtoMapper = projectDtoMapper;
    }

    @Transactional
    SupervisorProjectDetailDto addProjectMembers(
            User supervisor,
            String projectId,
            AddSupervisorProjectMembersRequest request) {
        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, parseProjectId(projectId));

        List<User> studentsToAdd = resolveStudents(request.getStudentIds());
        for (User student : studentsToAdd) {
            if (projectMemberRepository.existsByUserIdAndProjectId(student.getId(), project.getId())) {
                throw new ValidationException("studentIds", "One or more selected students are already assigned.");
            }
        }

        Instant now = Instant.now();
        for (User student : studentsToAdd) {
            projectMemberRepository.save(buildProjectMember(project.getId(), student.getId(), Roles.STUDENT, now));
        }

        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        projectRepository.save(project);

        return projectDtoMapper.toProjectDetail(project);
    }

    @Transactional(readOnly = true)
    List<StudentSearchResultDto> searchStudents(String query) {
        String normalizedQuery = NormalizationUtils.normalizeEmail(query);
        if (normalizedQuery == null || normalizedQuery.length() < 3) {
            return List.of();
        }

        return userRepository
                .findTop10ByRoleAndEmailContainingIgnoreCaseOrderByEmailAsc(Roles.STUDENT, normalizedQuery)
                .stream()
                .map(projectDtoMapper::toStudentSearchResult)
                .toList();
    }

    List<User> resolveStudents(List<UUID> requestedStudentIds) {
        Set<UUID> uniqueIds = new LinkedHashSet<>(requestedStudentIds);
        if (uniqueIds.size() != requestedStudentIds.size()) {
            throw new ValidationException("studentIds", "Duplicate students are not allowed.");
        }

        List<User> students = userRepository.findAllById(uniqueIds);
        if (students.size() != uniqueIds.size()) {
            throw new ValidationException("studentIds", "One or more selected students were not found.");
        }

        boolean containsNonStudent = students.stream()
                .anyMatch(user -> !Roles.STUDENT.equals(user.getRole()));
        if (containsNonStudent) {
            throw new ValidationException("studentIds", "Only student accounts can be assigned to a project.");
        }

        return students;
    }

    UUID resolveLeaderForCreate(UUID leaderStudentId, List<User> students) {
        if (leaderStudentId == null) {
            return null;
        }

        boolean leaderIncluded = students.stream()
                .map(User::getId)
                .anyMatch(id -> Objects.equals(id, leaderStudentId));
        if (!leaderIncluded) {
            throw new ValidationException(
                    "leaderStudentId",
                    "Leader must be one of the selected student members.");
        }

        return leaderStudentId;
    }

    void validateLeaderAssignment(UUID projectId, UUID leaderStudentId) {
        boolean isStudentMember = projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(
                leaderStudentId,
                projectId,
                Roles.STUDENT);
        if (!isStudentMember) {
            throw new ValidationException(
                    "leaderStudentId",
                    "Leader must be an assigned student of this project.");
        }
    }

    ProjectMember buildProjectMember(
            UUID projectId,
            UUID userId,
            String memberRole,
            Instant createdAt) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setMemberRole(memberRole);
        member.setCreatedAt(createdAt);
        return member;
    }

    private UUID parseProjectId(String projectId) {
        return EntityIdParser.parseOrNotFound(projectId);
    }
}
