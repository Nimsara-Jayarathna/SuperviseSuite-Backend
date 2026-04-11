package com.supervisesuite.backend.auth.dto;

public class RegisterConfigResponse {

    private final boolean domainRestrictionEnabled;
    private final String studentDomain;
    private final String supervisorDomain;

    public RegisterConfigResponse(
        boolean domainRestrictionEnabled,
        String studentDomain,
        String supervisorDomain
    ) {
        this.domainRestrictionEnabled = domainRestrictionEnabled;
        this.studentDomain = studentDomain;
        this.supervisorDomain = supervisorDomain;
    }

    public boolean isDomainRestrictionEnabled() {
        return domainRestrictionEnabled;
    }

    public String getStudentDomain() {
        return studentDomain;
    }

    public String getSupervisorDomain() {
        return supervisorDomain;
    }
}
