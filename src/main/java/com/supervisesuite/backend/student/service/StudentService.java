package com.supervisesuite.backend.student.service;

import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import java.util.List;

public interface StudentService {
    List<StudentProjectSummaryDto> getProjects(String authenticatedUserId);
}
