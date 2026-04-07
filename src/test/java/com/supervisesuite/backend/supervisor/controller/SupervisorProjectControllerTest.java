package com.supervisesuite.backend.supervisor.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.security.CookieService;
import com.supervisesuite.backend.auth.security.JwtService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@Import(TestcontainersConfiguration.class)
class SupervisorProjectControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

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
    void patchRepository_validRequest_returns200() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor1@university.ac.lk");
        Project project = persistProject(supervisor);
        String token = jwtService.generateAccessToken(supervisor);
        String repositoryUrl = "https://github.com/facebook/react";

        ResponseEntity<Map> response = patchRepository(
            project.getId(),
            Map.of("repositoryUrl", repositoryUrl),
            token
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> github = (Map<String, Object>) data.get("github");
        assertThat(github).isNotNull();
        assertThat(github.get("repositoryUrl")).isEqualTo(repositoryUrl);
    }

    @Test
    void patchRepository_successResponse_containsRepositoryUrlInPayload() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor2@university.ac.lk");
        Project project = persistProject(supervisor);
        String token = jwtService.generateAccessToken(supervisor);
        String repositoryUrl = "https://github.com/microsoft/vscode";

        ResponseEntity<Map> response = patchRepository(
            project.getId(),
            Map.of("repositoryUrl", repositoryUrl),
            token
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> github = (Map<String, Object>) data.get("github");
        assertThat(github).isNotNull();
        assertThat(github.get("repositoryUrl")).isEqualTo(repositoryUrl);
    }

    @Test
    void patchRepository_invalidUrl_returns400() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor3@university.ac.lk");
        String token = jwtService.generateAccessToken(supervisor);

        ResponseEntity<Map> response = patchRepository(
            UUID.randomUUID(),
            Map.of("repositoryUrl", "http://github.com/user/repo"),
            token
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(error(response).get("code")).isEqualTo("VALIDATION_ERROR");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) error(response).get("details");
        assertThat(details).anyMatch(detail -> "repositoryUrl".equals(detail.get("field")));
    }

    @Test
    void patchRepository_nonGithubUrl_returns400() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor4@university.ac.lk");
        String token = jwtService.generateAccessToken(supervisor);

        ResponseEntity<Map> response = patchRepository(
            UUID.randomUUID(),
            Map.of("repositoryUrl", "https://gitlab.com/user/repo"),
            token
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(error(response).get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void patchRepository_unauthenticated_returns401() {
        ResponseEntity<Map> response = patchRepository(
            UUID.randomUUID(),
            Map.of("repositoryUrl", "https://github.com/facebook/react"),
            null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(error(response).get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void patchRepository_wrongRole_returns403() {
        User student = persistUser(Roles.STUDENT, "student1@university.ac.lk");
        String token = jwtService.generateAccessToken(student);

        ResponseEntity<Map> response = patchRepository(
            UUID.randomUUID(),
            Map.of("repositoryUrl", "https://github.com/facebook/react"),
            token
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(error(response).get("code")).isEqualTo("FORBIDDEN");
    }

    @Test
    void getProjectJiraHealth_validRequest_returns200AndHealthPayload() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira@university.ac.lk");
        Project project = persistProject(supervisor);
        String token = jwtService.generateAccessToken(supervisor);

        ResponseEntity<Map> response = getProjectJiraHealth(project.getId(), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data).containsKeys("completionPercent", "openIssues", "statusBreakdown", "bugRatio");
    }

    @Test
    void refreshProjectJira_withoutActiveIntegration_returns400() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira-refresh@university.ac.lk");
        Project project = persistProject(supervisor);
        String token = jwtService.generateAccessToken(supervisor);

        ResponseEntity<Map> response = refreshProjectJira(project.getId(), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(error(response).get("code")).isEqualTo("VALIDATION_ERROR");
    }

    private ResponseEntity<Map> patchRepository(UUID projectId, Map<String, Object> body, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.add(HttpHeaders.COOKIE, CookieService.ACCESS_TOKEN_COOKIE + "=" + accessToken);
        }

        return restTemplate.exchange(
            "/api/supervisor/projects/" + projectId + "/repository",
            HttpMethod.PATCH,
            new HttpEntity<>(body, headers),
            Map.class
        );
    }

    private ResponseEntity<Map> getProjectJiraHealth(UUID projectId, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        if (accessToken != null) {
            headers.add(HttpHeaders.COOKIE, CookieService.ACCESS_TOKEN_COOKIE + "=" + accessToken);
        }

        return restTemplate.exchange(
            "/api/supervisor/projects/" + projectId + "/jira/health",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
    }

    private ResponseEntity<Map> refreshProjectJira(UUID projectId, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        if (accessToken != null) {
            headers.add(HttpHeaders.COOKIE, CookieService.ACCESS_TOKEN_COOKIE + "=" + accessToken);
        }

        return restTemplate.exchange(
            "/api/supervisor/projects/" + projectId + "/jira/refresh",
            HttpMethod.POST,
            new HttpEntity<>(headers),
            Map.class
        );
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

    private Project persistProject(User supervisor) {
        Project project = new Project();
        project.setCreatedAt(Instant.now());
        project.setName("Project Phoenix");
        project.setDescription("Repository linking feature");
        project.setStatus("ACTIVE");
        project.setBatch("2025");
        project.setSemester("Y3S2");
        project.setSupervisor(supervisor);
        return projectRepository.save(project);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> error(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("error");
    }
}
