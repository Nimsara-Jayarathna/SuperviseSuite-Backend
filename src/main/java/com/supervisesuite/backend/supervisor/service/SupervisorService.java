package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMembersRequest;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.SupervisorDashboardDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectSummaryDto;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectStatusRequest;
import java.util.List;

public interface SupervisorService {
    SupervisorDashboardDto getDashboard(String authenticatedUserId);

    List<SupervisorProjectSummaryDto> getProjects(String authenticatedUserId);

    SupervisorProjectDetailDto getProjectById(String authenticatedUserId, String projectId);

    SupervisorProjectDetailDto updateProject(
        String authenticatedUserId,
        String projectId,
        UpdateSupervisorProjectRequest request
    );

    SupervisorProjectDetailDto updateProjectStatus(
        String authenticatedUserId,
        String projectId,
        UpdateSupervisorProjectStatusRequest request
    );

    SupervisorProjectDetailDto addProjectMembers(
        String authenticatedUserId,
        String projectId,
        AddSupervisorProjectMembersRequest request
    );

    SupervisorProjectDetailDto addProjectMilestone(
        String authenticatedUserId,
        String projectId,
        AddSupervisorProjectMilestoneRequest request
    );

    SupervisorProjectDetailDto updateProjectMilestone(
        String authenticatedUserId,
        String projectId,
        String milestoneId,
        UpdateSupervisorProjectMilestoneRequest request
    );

    List<StudentSearchResultDto> searchStudents(String query);

    CreateSupervisorProjectResponse createProject(
        String authenticatedUserId,
        CreateSupervisorProjectRequest request
    );
}
