package com.supervisesuite.backend.student.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.security.CookieService;
import com.supervisesuite.backend.auth.security.JwtService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.memberships.entity.ProjectMember;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
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
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@Import(TestcontainersConfiguration.class)
class StudentJiraControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

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
    void getProjectJiraHealth_whenStudentIsMember_returns200AndPayload() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira-student-test@university.ac.lk");
        User student = persistUser(Roles.STUDENT, "student-jira-test@university.ac.lk");
        Project project = persistProject(supervisor);
        persistMembership(student.getId(), project.getId(), Roles.STUDENT);

        String token = jwtService.generateAccessToken(student);

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
    void getProjectJiraHealth_whenStudentNotMember_returns404() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira-student-test2@university.ac.lk");
        User student = persistUser(Roles.STUDENT, "student-jira-test2@university.ac.lk");
        Project project = persistProject(supervisor);

        String token = jwtService.generateAccessToken(student);

        ResponseEntity<Map> response = getProjectJiraHealth(project.getId(), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);
    }

    @Test
    void getProjectJiraHierarchy_whenStudentIsMemberAndIssuesExist_returns200AndPayload() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira-hierarchy-student@university.ac.lk");
        User student = persistUser(Roles.STUDENT, "student-jira-hierarchy@university.ac.lk");
        Project project = persistProject(supervisor);
        persistMembership(student.getId(), project.getId(), Roles.STUDENT);

        projectJiraIssueRepository.save(persistIssue(project.getId(), "PRJ-1", null, "Epic"));
        projectJiraIssueRepository.save(persistIssue(project.getId(), "PRJ-2", "PRJ-1", "Story"));

        String token = jwtService.generateAccessToken(student);

        ResponseEntity<Map> response = getProjectJiraHierarchy(project.getId(), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsKeys("roots", "orphans");
    }

    @Test
    void getProjectJiraHierarchy_whenStudentIsMemberAndNoIssues_returns200WithEmptyLists() {
        User supervisor = persistUser(Roles.SUPERVISOR, "supervisor-jira-hierarchy-student-empty@university.ac.lk");
        User student = persistUser(Roles.STUDENT, "student-jira-hierarchy-empty@university.ac.lk");
        Project project = persistProject(supervisor);
        persistMembership(student.getId(), project.getId(), Roles.STUDENT);

        String token = jwtService.generateAccessToken(student);

        ResponseEntity<Map> response = getProjectJiraHierarchy(project.getId(), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsEntry("roots", List.of());
        assertThat(data).containsEntry("orphans", List.of());
    }

    private ResponseEntity<Map> getProjectJiraHealth(UUID projectId, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, CookieService.ACCESS_TOKEN_COOKIE + "=" + accessToken);

        return restTemplate.exchange(
            "/api/student/projects/" + projectId + "/jira/health",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
    }

    private ResponseEntity<Map> getProjectJiraHierarchy(UUID projectId, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, CookieService.ACCESS_TOKEN_COOKIE + "=" + accessToken);

        return restTemplate.exchange(
            "/api/student/projects/" + projectId + "/jira/hierarchy",
            HttpMethod.GET,
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
        project.setName("Project Atlas");
        project.setDescription("Student Jira health integration test");
        project.setStatus("ACTIVE");
        project.setBatch("2025");
        project.setSemester("Y3S2");
        project.setSupervisor(supervisor);
        return projectRepository.save(project);
    }

    private void persistMembership(UUID userId, UUID projectId, String role) {
        ProjectMember member = new ProjectMember();
        member.setCreatedAt(Instant.now());
        member.setUserId(userId);
        member.setProjectId(projectId);
        member.setMemberRole(role);
        projectMemberRepository.save(member);
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
}
