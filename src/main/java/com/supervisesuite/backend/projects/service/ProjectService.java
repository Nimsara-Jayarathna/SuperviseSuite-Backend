package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.projects.dto.ProjectCommitActivityDto;

public interface ProjectService {
    ProjectCommitActivityDto getCommitActivity(String repositoryUrl);
}