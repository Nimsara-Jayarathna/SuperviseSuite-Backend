package com.supervisesuite.backend.student.service;

import com.supervisesuite.backend.student.dto.StudentProjectDetailDto;
import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import java.util.List;
import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.dto.JiraHierarchyDto;
import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.meetings.dto.CreateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.MeetingChannelDto;

public interface StudentService {
    List<StudentProjectSummaryDto> getProjects(String authenticatedUserId);

    StudentProjectDetailDto getProjectById(String authenticatedUserId, String projectId);

    ProjectGitHubDashboardDto getProjectGitHubDashboard(
        String authenticatedUserId,
        String projectId,
        String linkedRepositoryId
    );

    ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getProjectGitHubActivityPage(
        String authenticatedUserId,
        String projectId,
        String linkedRepositoryId,
        int page,
        int size
    );

    ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getProjectGitHubContributorsPage(
        String authenticatedUserId,
        String projectId,
        String linkedRepositoryId,
        int page,
        int size
    );

    JiraHealthDto getJiraHealthOverview(String authenticatedUserId, String projectId);

    JiraSprintProgressDto getJiraSprintProgress(String authenticatedUserId, String projectId);

    JiraWorkloadDto getJiraWorkload(String authenticatedUserId, String projectId);

    JiraHierarchyDto getJiraHierarchy(String authenticatedUserId, String projectId);

    List<MeetingChannelDto> getProjectMeetingChannels(String authenticatedUserId, String projectId);

    MeetingChannelDto addProjectMeetingChannel(
        String authenticatedUserId,
        String projectId,
        CreateMeetingChannelRequest request
    );
}
