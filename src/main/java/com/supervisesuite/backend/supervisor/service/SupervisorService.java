package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectSummaryDto;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import java.util.List;

public interface SupervisorService {
    List<SupervisorProjectSummaryDto> getProjects(String authenticatedUserId);

    SupervisorProjectDetailDto getProjectById(String authenticatedUserId, String projectId);

    List<StudentSearchResultDto> searchStudents(String query);

    CreateSupervisorProjectResponse createProject(
        String authenticatedUserId,
        CreateSupervisorProjectRequest request
    );
}
