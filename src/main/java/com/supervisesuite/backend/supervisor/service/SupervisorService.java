package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import java.util.List;

public interface SupervisorService {
    List<StudentSearchResultDto> searchStudents(String query);

    CreateSupervisorProjectResponse createProject(
        String authenticatedUserId,
        CreateSupervisorProjectRequest request
    );
}
