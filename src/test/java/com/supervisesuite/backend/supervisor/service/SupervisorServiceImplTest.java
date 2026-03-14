package com.supervisesuite.backend.supervisor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.dto.UpdateRepositoryRequest;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SupervisorServiceImplTest {

    @Autowired
    private SupervisorService supervisorService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectMilestoneRepository projectMilestoneRepository;

    @BeforeEach
    void cleanUp() {
        projectMilestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void updateRepository_validUrl_updatesRepositoryUrl() {
        User supervisor = persistUser(Roles.SUPERVISOR, "owner1@university.ac.lk");
        Project project = persistProject(supervisor, null, null, null);

        UpdateRepositoryRequest request = new UpdateRepositoryRequest();
        request.setRepositoryUrl("https://github.com/facebook/react");

        SupervisorProjectDetailDto result = supervisorService.updateRepository(
            supervisor.getId().toString(),
            project.getId().toString(),
            request
        );

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();

        assertThat(reloaded.getRepositoryUrl()).isEqualTo("https://github.com/facebook/react");
        assertThat(result.getRepositoryUrl()).isEqualTo("https://github.com/facebook/react");
    }

    @Test
    void updateRepository_nullUrl_clearsRepositoryUrl() {
        User supervisor = persistUser(Roles.SUPERVISOR, "owner2@university.ac.lk");
        Project project = persistProject(
            supervisor,
            "https://github.com/example/legacy-repo",
            Instant.parse("2025-01-10T10:15:30Z"),
            Instant.parse("2025-01-10T10:15:30Z")
        );

        UpdateRepositoryRequest request = new UpdateRepositoryRequest();
        request.setRepositoryUrl(null);

        SupervisorProjectDetailDto result = supervisorService.updateRepository(
            supervisor.getId().toString(),
            project.getId().toString(),
            request
        );

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();

        assertThat(reloaded.getRepositoryUrl()).isNull();
        assertThat(result.getRepositoryUrl()).isNull();
    }

    @Test
    void updateRepository_nonOwnerSupervisor_returnsNotFound() {
        User owner = persistUser(Roles.SUPERVISOR, "owner3@university.ac.lk");
        User anotherSupervisor = persistUser(Roles.SUPERVISOR, "owner4@university.ac.lk");
        Project project = persistProject(owner, null, null, null);

        UpdateRepositoryRequest request = new UpdateRepositoryRequest();
        request.setRepositoryUrl("https://github.com/microsoft/vscode");

        assertThatThrownBy(() -> supervisorService.updateRepository(
            anotherSupervisor.getId().toString(),
            project.getId().toString(),
            request
        )).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateRepository_projectNotFound_returnsNotFound() {
        User supervisor = persistUser(Roles.SUPERVISOR, "owner5@university.ac.lk");

        UpdateRepositoryRequest request = new UpdateRepositoryRequest();
        request.setRepositoryUrl("https://github.com/microsoft/vscode");

        assertThatThrownBy(() -> supervisorService.updateRepository(
            supervisor.getId().toString(),
            UUID.randomUUID().toString(),
            request
        )).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateRepository_softDeletedProject_returnsNotFound() {
        User supervisor = persistUser(Roles.SUPERVISOR, "owner6@university.ac.lk");
        Project project = persistProject(supervisor, null, null, null);
        project.setDeletedAt(Instant.now());
        projectRepository.save(project);

        UpdateRepositoryRequest request = new UpdateRepositoryRequest();
        request.setRepositoryUrl("https://github.com/microsoft/vscode");

        assertThatThrownBy(() -> supervisorService.updateRepository(
            supervisor.getId().toString(),
            project.getId().toString(),
            request
        )).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateRepository_updatesUpdatedAtAndLastActivityAt() {
        User supervisor = persistUser(Roles.SUPERVISOR, "owner7@university.ac.lk");
        Instant previous = Instant.parse("2025-01-10T10:15:30Z");
        Project project = persistProject(
            supervisor,
            null,
            previous,
            previous
        );

        UpdateRepositoryRequest request = new UpdateRepositoryRequest();
        request.setRepositoryUrl("https://github.com/org_name/repo_name");

        supervisorService.updateRepository(supervisor.getId().toString(), project.getId().toString(), request);

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(reloaded.getUpdatedAt()).isAfter(previous);
        assertThat(reloaded.getLastActivityAt()).isAfter(previous);
    }

    private User persistUser(String role, String email) {
        User user = new User();
        user.setCreatedAt(Instant.now());
        user.setEmail(email);
        user.setRole(role);
        user.setFirstName("Test");
        user.setLastName("User");
        return userRepository.save(user);
    }

    private Project persistProject(User supervisor, String repositoryUrl, Instant updatedAt, Instant lastActivityAt) {
        Project project = new Project();
        project.setCreatedAt(Instant.now());
        project.setName("Project Phoenix");
        project.setDescription("Repository linking feature");
        project.setStatus("ACTIVE");
        project.setBatch("2025");
        project.setSemester("Y3S2");
        project.setSupervisor(supervisor);
        project.setRepositoryUrl(repositoryUrl);
        project.setUpdatedAt(updatedAt);
        project.setLastActivityAt(lastActivityAt);
        return projectRepository.save(project);
    }
}
