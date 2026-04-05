package com.supervisesuite.backend.supervisor.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class SupervisorDashboardDto {
    private int totalProjects;
    private int planningProjects;
    private int activeProjects;
    private int atRiskProjects;
    private int behindProjects;
    private int completedProjects;
    private int upcomingMilestonesCount;
    private int jiraAtRiskCount;
    private int jiraBehindCount;
    private List<ProjectItem> projects;
    private List<ProjectItem> recentProjects;

    public SupervisorDashboardDto() {
    }

    public SupervisorDashboardDto(
        int totalProjects,
        int planningProjects,
        int activeProjects,
        int atRiskProjects,
        int behindProjects,
        int completedProjects,
        int upcomingMilestonesCount,
        List<ProjectItem> projects,
        List<ProjectItem> recentProjects
    ) {
        this.totalProjects = totalProjects;
        this.planningProjects = planningProjects;
        this.activeProjects = activeProjects;
        this.atRiskProjects = atRiskProjects;
        this.behindProjects = behindProjects;
        this.completedProjects = completedProjects;
        this.upcomingMilestonesCount = upcomingMilestonesCount;
        this.projects = projects;
        this.recentProjects = recentProjects;
    }

    public int getTotalProjects() {
        return totalProjects;
    }

    public void setTotalProjects(int totalProjects) {
        this.totalProjects = totalProjects;
    }

    public int getPlanningProjects() {
        return planningProjects;
    }

    public void setPlanningProjects(int planningProjects) {
        this.planningProjects = planningProjects;
    }

    public int getActiveProjects() {
        return activeProjects;
    }

    public void setActiveProjects(int activeProjects) {
        this.activeProjects = activeProjects;
    }

    public int getAtRiskProjects() {
        return atRiskProjects;
    }

    public void setAtRiskProjects(int atRiskProjects) {
        this.atRiskProjects = atRiskProjects;
    }

    public int getBehindProjects() {
        return behindProjects;
    }

    public void setBehindProjects(int behindProjects) {
        this.behindProjects = behindProjects;
    }

    public int getCompletedProjects() {
        return completedProjects;
    }

    public void setCompletedProjects(int completedProjects) {
        this.completedProjects = completedProjects;
    }

    public int getUpcomingMilestonesCount() {
        return upcomingMilestonesCount;
    }

    public void setUpcomingMilestonesCount(int upcomingMilestonesCount) {
        this.upcomingMilestonesCount = upcomingMilestonesCount;
    }

    public List<ProjectItem> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectItem> projects) {
        this.projects = projects;
    }

    public List<ProjectItem> getRecentProjects() {
        return recentProjects;
    }

    public void setRecentProjects(List<ProjectItem> recentProjects) {
        this.recentProjects = recentProjects;
    }

    public int getJiraAtRiskCount() {
        return jiraAtRiskCount;
    }

    public void setJiraAtRiskCount(int jiraAtRiskCount) {
        this.jiraAtRiskCount = jiraAtRiskCount;
    }

    public int getJiraBehindCount() {
        return jiraBehindCount;
    }

    public void setJiraBehindCount(int jiraBehindCount) {
        this.jiraBehindCount = jiraBehindCount;
    }

    public static class ProjectItem {
        private UUID id;
        private String title;
        private String summary;
        private String lifecycleStatus;
        private LocalDate milestoneDate;
        private Instant lastActivityAt;
        private Integer progressPercent;
        private String healthNote;
        private String jiraHealthIndicator;

        public ProjectItem() {
        }

        public ProjectItem(
            UUID id,
            String title,
            String summary,
            String lifecycleStatus,
            LocalDate milestoneDate,
            Instant lastActivityAt,
            Integer progressPercent,
            String healthNote
        ) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.lifecycleStatus = lifecycleStatus;
            this.milestoneDate = milestoneDate;
            this.lastActivityAt = lastActivityAt;
            this.progressPercent = progressPercent;
            this.healthNote = healthNote;
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

        public String getLifecycleStatus() {
            return lifecycleStatus;
        }

        public void setLifecycleStatus(String lifecycleStatus) {
            this.lifecycleStatus = lifecycleStatus;
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

        public String getHealthNote() {
            return healthNote;
        }

        public void setHealthNote(String healthNote) {
            this.healthNote = healthNote;
        }

        public String getJiraHealthIndicator() {
            return jiraHealthIndicator;
        }

        public void setJiraHealthIndicator(String jiraHealthIndicator) {
            this.jiraHealthIndicator = jiraHealthIndicator;
        }
    }
}
