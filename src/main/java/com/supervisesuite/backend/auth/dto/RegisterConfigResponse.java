package com.supervisesuite.backend.auth.dto;

public class RegisterConfigResponse {

    private final boolean domainRestrictionEnabled;
    private final String studentDomain;
    private final String supervisorDomain;
    private final boolean studentEmailPrefixRestrictionEnabled;
    private final String studentEmailPrefixRegex;

    public RegisterConfigResponse(
        boolean domainRestrictionEnabled,
        String studentDomain,
        String supervisorDomain,
        boolean studentEmailPrefixRestrictionEnabled,
        String studentEmailPrefixRegex
    ) {
        this.domainRestrictionEnabled = domainRestrictionEnabled;
        this.studentDomain = studentDomain;
        this.supervisorDomain = supervisorDomain;
        this.studentEmailPrefixRestrictionEnabled = studentEmailPrefixRestrictionEnabled;
        this.studentEmailPrefixRegex = studentEmailPrefixRegex;
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

    public boolean isStudentEmailPrefixRestrictionEnabled() {
        return studentEmailPrefixRestrictionEnabled;
    }

    public String getStudentEmailPrefixRegex() {
        return studentEmailPrefixRegex;
    }
}
