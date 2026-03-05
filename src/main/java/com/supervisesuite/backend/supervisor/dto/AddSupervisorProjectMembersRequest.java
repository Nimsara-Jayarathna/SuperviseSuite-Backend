package com.supervisesuite.backend.supervisor.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public class AddSupervisorProjectMembersRequest {
    @NotEmpty(message = "At least one student must be selected.")
    private List<@NotNull(message = "Student ID is required.") UUID> studentIds;

    public List<UUID> getStudentIds() {
        return studentIds;
    }

    public void setStudentIds(List<UUID> studentIds) {
        this.studentIds = studentIds;
    }
}
