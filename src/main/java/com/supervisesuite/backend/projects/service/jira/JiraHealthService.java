package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import java.util.UUID;

public interface JiraHealthService {

    JiraHealthDto getHealthOverview(UUID projectId);
}
