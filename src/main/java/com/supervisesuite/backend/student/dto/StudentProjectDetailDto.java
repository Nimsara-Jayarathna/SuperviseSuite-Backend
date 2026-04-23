package com.supervisesuite.backend.student.dto;

import com.supervisesuite.backend.projectfiles.dto.ProjectFileDto;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileListDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoriesDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class StudentProjectDetailDto {
    private UUID id;
    private String title;
    private String summary;
    private String status;
    private String batch;
    private String semester;
    private LocalDate milestoneDate;
    private Instant lastActivityAt;
    private Integer progressPercent;
    private ProjectGitHubPreviewDto github;
    private ProjectGitHubRepositoriesDto githubRepositories;
    private JiraIntegration jira;
    private Leader leader;
    private List<Member> members;
    private List<Milestone> milestones;
    private Files files;

    public StudentProjectDetailDto() {
    }

    public StudentProjectDetailDto(
        UUID id,
        String title,
        String summary,
        String status,
        String batch,
        String semester,
        LocalDate milestoneDate,
        Instant lastActivityAt,
        Integer progressPercent,
        ProjectGitHubPreviewDto github,
        ProjectGitHubRepositoriesDto githubRepositories,
        JiraIntegration jira,
        Leader leader,
        List<Member> members,
        List<Milestone> milestones
    ) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.status = status;
        this.batch = batch;
        this.semester = semester;
        this.milestoneDate = milestoneDate;
        this.lastActivityAt = lastActivityAt;
        this.progressPercent = progressPercent;
        this.github = github;
        this.githubRepositories = githubRepositories;
        this.jira = jira;
        this.leader = leader;
        this.members = members;
        this.milestones = milestones;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public LocalDate getMilestoneDate() {
        return milestoneDate;
    }

    public void setMilestoneDate(LocalDate milestoneDate) {
        this.milestoneDate = milestoneDate;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public ProjectGitHubPreviewDto getGithub() {
        return github;
    }

    public void setGithub(ProjectGitHubPreviewDto github) {
        this.github = github;
    }

    public ProjectGitHubRepositoriesDto getGithubRepositories() {
        return githubRepositories;
    }

    public void setGithubRepositories(ProjectGitHubRepositoriesDto githubRepositories) {
        this.githubRepositories = githubRepositories;
    }

    public JiraIntegration getJira() {
        return jira;
    }

    public void setJira(JiraIntegration jira) {
        this.jira = jira;
    }

    public Leader getLeader() {
        return leader;
    }

    public void setLeader(Leader leader) {
        this.leader = leader;
    }

    public List<Member> getMembers() {
        return members;
    }

    public void setMembers(List<Member> members) {
        this.members = members;
    }

    public List<Milestone> getMilestones() {
        return milestones;
    }

    public void setMilestones(List<Milestone> milestones) {
        this.milestones = milestones;
    }

    public Files getFiles() {
        return files;
    }

    public void setFiles(Files files) {
        this.files = files;
    }

    public static class Files {
        private List<ProjectFileDto> items;
        private ProjectFileListDto.Config config;

        public Files() {
        }

        public Files(List<ProjectFileDto> items, ProjectFileListDto.Config config) {
            this.items = items;
            this.config = config;
        }

        public List<ProjectFileDto> getItems() {
            return items;
        }

        public void setItems(List<ProjectFileDto> items) {
            this.items = items;
        }

        public ProjectFileListDto.Config getConfig() {
            return config;
        }

        public void setConfig(ProjectFileListDto.Config config) {
            this.config = config;
        }
    }

    public static class Member {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
        private String registrationNumber;
        private String memberRole;

        public Member() {
        }

        public Member(
            UUID id,
            String firstName,
            String lastName,
            String email,
            String registrationNumber,
            String memberRole
        ) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.registrationNumber = registrationNumber;
            this.memberRole = memberRole;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getRegistrationNumber() {
            return registrationNumber;
        }

        public void setRegistrationNumber(String registrationNumber) {
            this.registrationNumber = registrationNumber;
        }

        public String getMemberRole() {
            return memberRole;
        }

        public void setMemberRole(String memberRole) {
            this.memberRole = memberRole;
        }
    }

    public static class Leader {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
        private String registrationNumber;

        public Leader() {
        }

        public Leader(
            UUID id,
            String firstName,
            String lastName,
            String email,
            String registrationNumber
        ) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.registrationNumber = registrationNumber;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getRegistrationNumber() {
            return registrationNumber;
        }

        public void setRegistrationNumber(String registrationNumber) {
            this.registrationNumber = registrationNumber;
        }
    }

    public static class Milestone {
        private UUID id;
        private String title;
        private String description;
        private LocalDate dueDate;
        private String status;
        private Integer sequenceNo;

        public Milestone() {
        }

        public Milestone(
            UUID id,
            String title,
            String description,
            LocalDate dueDate,
            String status,
            Integer sequenceNo
        ) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.dueDate = dueDate;
            this.status = status;
            this.sequenceNo = sequenceNo;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public LocalDate getDueDate() {
            return dueDate;
        }

        public void setDueDate(LocalDate dueDate) {
            this.dueDate = dueDate;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getSequenceNo() {
            return sequenceNo;
        }

        public void setSequenceNo(Integer sequenceNo) {
            this.sequenceNo = sequenceNo;
        }
    }

    public static class JiraIntegration {
        private boolean connected;
        private String workspaceName;
        private String workspaceUrl;
        private Instant lastSyncedAt;
        private String syncStatus;

        public JiraIntegration() {
        }

        public JiraIntegration(
            boolean connected,
            String workspaceName,
            String workspaceUrl,
            Instant lastSyncedAt,
            String syncStatus
        ) {
            this.connected = connected;
            this.workspaceName = workspaceName;
            this.workspaceUrl = workspaceUrl;
            this.lastSyncedAt = lastSyncedAt;
            this.syncStatus = syncStatus;
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }

        public String getWorkspaceName() {
            return workspaceName;
        }

        public void setWorkspaceName(String workspaceName) {
            this.workspaceName = workspaceName;
        }

        public String getWorkspaceUrl() {
            return workspaceUrl;
        }

        public void setWorkspaceUrl(String workspaceUrl) {
            this.workspaceUrl = workspaceUrl;
        }

        public Instant getLastSyncedAt() {
            return lastSyncedAt;
        }

        public void setLastSyncedAt(Instant lastSyncedAt) {
            this.lastSyncedAt = lastSyncedAt;
        }

        public String getSyncStatus() {
            return syncStatus;
        }

        public void setSyncStatus(String syncStatus) {
            this.syncStatus = syncStatus;
        }
    }
}
