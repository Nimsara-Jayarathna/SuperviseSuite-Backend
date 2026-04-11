package com.supervisesuite.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.registration")
public class RegistrationProperties {

    private String studentEmailDomain;
    private String supervisorEmailDomain;
    private long otpExpirySeconds = 600;
    private long sessionExpirySeconds = 600;
    private Cleanup cleanup = new Cleanup();

    public boolean hasAnyDomainRestriction() {
        return hasStudentDomain() || hasSupervisorDomain();
    }

    public boolean hasStudentDomain() {
        return studentEmailDomain != null && !studentEmailDomain.isBlank();
    }

    public boolean hasSupervisorDomain() {
        return supervisorEmailDomain != null && !supervisorEmailDomain.isBlank();
    }

    public String inferRole(String normalizedEmail) {
        if (hasStudentDomain() && normalizedEmail.endsWith(studentEmailDomain)) {
            return "STUDENT";
        }
        if (hasSupervisorDomain() && normalizedEmail.endsWith(supervisorEmailDomain)) {
            return "SUPERVISOR";
        }
        return null;
    }

    public boolean isEmailAllowed(String normalizedEmail) {
        if (!hasAnyDomainRestriction()) {
            return true;
        }
        return inferRole(normalizedEmail) != null;
    }

    @Getter
    @Setter
    public static class Cleanup {
        private boolean enabled = true;
        private long initialDelayMs = 120000;
        private long fixedDelayMs = 900000;
    }
}
