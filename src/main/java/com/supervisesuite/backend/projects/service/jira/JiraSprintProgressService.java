package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import java.util.UUID;

public interface JiraSprintProgressService {

    JiraSprintProgressDto getSprintProgress(UUID projectId);
}
