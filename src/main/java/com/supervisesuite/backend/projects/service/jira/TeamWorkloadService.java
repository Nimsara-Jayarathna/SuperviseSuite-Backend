package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.TeamWorkloadResponseDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;

public interface TeamWorkloadService {

    TeamWorkloadResponseDto computeWorkload(ProjectJiraIntegration integration);
}
