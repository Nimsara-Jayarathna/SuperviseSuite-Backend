package com.supervisesuite.backend.supervisor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.security.CookieService;
import com.supervisesuite.backend.auth.security.JwtService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    webEnvironment = WebEnvironment.MOCK,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SupervisorProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectJiraIssueRepository projectJiraIssueRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectMilestoneRepository projectMilestoneRepository;

    @BeforeEach
    void cleanUp() {
        projectMilestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectJiraIssueRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void patchRepository_validRequest_returns200() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor1@university.ac.lk");
        Project project = persistProject(supervisor);
        String token = jwtService.generateAccessToken(supervisor);
        String repositoryUrl = "https://github.com/facebook/react";

        MvcResult response = patchRepository(
            project.getId(),
            Map.of("repositoryUrl", repositoryUrl),
            token
        );

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        Map<?, ?> body = body(response);
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
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

        MvcResult response = patchRepository(
            project.getId(),
            Map.of("repositoryUrl", repositoryUrl),
            token
        );

        Map<?, ?> body = body(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> github = (Map<String, Object>) data.get("github");
        assertThat(github).isNotNull();
        assertThat(github.get("repositoryUrl")).isEqualTo(repositoryUrl);
    }

    @Test
    void patchRepository_invalidUrl_returns400() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor3@university.ac.lk");
        String token = jwtService.generateAccessToken(supervisor);

        MvcResult response = patchRepository(
            UUID.randomUUID(),
            Map.of("repositoryUrl", "http://github.com/user/repo"),
            token
        );

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        Map<?, ?> body = body(response);
        assertThat(body).isNotNull();
        assertThat(error(body).get("code")).isEqualTo("VALIDATION_ERROR");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) error(body).get("details");
        assertThat(details).anyMatch(detail -> "repositoryUrl".equals(detail.get("field")));
    }

    @Test
    void patchRepository_nonGithubUrl_returns400() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor4@university.ac.lk");
        String token = jwtService.generateAccessToken(supervisor);

        MvcResult response = patchRepository(
            UUID.randomUUID(),
            Map.of("repositoryUrl", "https://gitlab.com/user/repo"),
            token
        );

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        Map<?, ?> body = body(response);
        assertThat(body).isNotNull();
        assertThat(error(body).get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void patchRepository_unauthenticated_returns401() {
        MvcResult response = patchRepository(
            UUID.randomUUID(),
            Map.of("repositoryUrl", "https://github.com/facebook/react"),
            null
        );

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        Map<?, ?> body = body(response);
        assertThat(body).isNotNull();
        assertThat(error(body).get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void patchRepository_wrongRole_returns403() {
        User student = persistUser(Roles.STUDENT, "student1@university.ac.lk");
        String token = jwtService.generateAccessToken(student);

        MvcResult response = patchRepository(
            UUID.randomUUID(),
            Map.of("repositoryUrl", "https://github.com/facebook/react"),
            token
        );

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        Map<?, ?> body = body(response);
        assertThat(body).isNotNull();
        assertThat(error(body).get("code")).isEqualTo("FORBIDDEN");
    }

    @Test
    void getProjectJiraHealth_validRequest_returns200AndHealthPayload() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira@university.ac.lk");
        Project project = persistProject(supervisor);
        String token = jwtService.generateAccessToken(supervisor);

        MvcResult response = getProjectJiraHealth(project.getId(), token);

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        Map<?, ?> body = body(response);
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).isNotNull();
        assertThat(data).containsKeys("completionPercent", "openIssues", "statusBreakdown", "bugRatio");
    }

    @Test
    void refreshProjectJira_withoutActiveIntegration_returns400() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira-refresh@university.ac.lk");
        Project project = persistProject(supervisor);
        String token = jwtService.generateAccessToken(supervisor);

        MvcResult response = refreshProjectJira(project.getId(), token);

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        Map<?, ?> body = body(response);
        assertThat(body).isNotNull();
        assertThat(error(body).get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void getProjectJiraHierarchy_whenIssuesExist_returns200AndHierarchyPayload() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira-hierarchy@university.ac.lk");
        Project project = persistProject(supervisor);
        String token = jwtService.generateAccessToken(supervisor);

        projectJiraIssueRepository.save(persistIssue(project.getId(), "PRJ-1", null, "Epic"));
        projectJiraIssueRepository.save(persistIssue(project.getId(), "PRJ-2", "PRJ-1", "Story"));

        MvcResult response = getProjectJiraHierarchy(project.getId(), token);

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        Map<?, ?> body = body(response);
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).containsKeys("roots", "orphans");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roots = (List<Map<String, Object>>) data.get("roots");
        assertThat(roots).hasSize(1);
    }

    @Test
    void getProjectJiraHierarchy_whenNoIssues_returns200WithEmptyLists() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira-hierarchy-empty@university.ac.lk");
        Project project = persistProject(supervisor);
        String token = jwtService.generateAccessToken(supervisor);

        MvcResult response = getProjectJiraHierarchy(project.getId(), token);

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        Map<?, ?> body = body(response);
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).containsEntry("roots", List.of());
        assertThat(data).containsEntry("orphans", List.of());
    }

    @Test
    void getProjectJiraHierarchy_whenProjectNotOwned_returns404() {
        User owner = persistUser(Roles.SUPERVISOR, "supervisor-jira-owner@university.ac.lk");
        User anotherSupervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira-other@university.ac.lk");
        Project project = persistProject(owner);
        String token = jwtService.generateAccessToken(anotherSupervisor);

        MvcResult response = getProjectJiraHierarchy(project.getId(), token);

        assertThat(response.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    private MvcResult patchRepository(UUID projectId, Map<String, Object> body, String accessToken) {
        try {
            var request = patch("/api/supervisor/projects/" + projectId + "/repository")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
            if (accessToken != null) {
                request.cookie(accessCookie(accessToken));
            }
            return mockMvc.perform(request).andReturn();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private MvcResult getProjectJiraHealth(UUID projectId, String accessToken) {
        try {
            var request = get("/api/supervisor/projects/" + projectId + "/jira/health");
            if (accessToken != null) {
                request.cookie(accessCookie(accessToken));
            }
            return mockMvc.perform(request).andReturn();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private MvcResult refreshProjectJira(UUID projectId, String accessToken) {
        try {
            var request = post("/api/supervisor/projects/" + projectId + "/jira/refresh");
            if (accessToken != null) {
                request.cookie(accessCookie(accessToken));
            }
            return mockMvc.perform(request).andReturn();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private MvcResult getProjectJiraHierarchy(UUID projectId, String accessToken) {
        try {
            var request = get("/api/supervisor/projects/" + projectId + "/jira/hierarchy");
            if (accessToken != null) {
                request.cookie(accessCookie(accessToken));
            }
            return mockMvc.perform(request).andReturn();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Cookie accessCookie(String accessToken) {
        return new Cookie(CookieService.ACCESS_TOKEN_COOKIE, accessToken);
    }

    private Map<?, ?> body(MvcResult result) {
        try {
            return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
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

    private ProjectJiraIssue persistIssue(UUID projectId, String issueKey, String parentKey, String issueType) {
        ProjectJiraIssue issue = new ProjectJiraIssue();
        issue.setProjectId(projectId);
        issue.setIssueKey(issueKey);
        issue.setSummary(issueKey + " summary");
        issue.setIssueType(issueType);
        issue.setStatusName("To Do");
        issue.setStatusCategoryKey("new");
        issue.setParentKey(parentKey);
        issue.setSyncedAt(Instant.now());
        return issue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> error(Map<?, ?> body) {
        return (Map<String, Object>) body.get("error");
    }
}
