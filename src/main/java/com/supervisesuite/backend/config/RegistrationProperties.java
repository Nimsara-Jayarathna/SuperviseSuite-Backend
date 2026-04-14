package com.supervisesuite.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.registration")
public class RegistrationProperties {

    private boolean domainRestrictionEnabled = true;
    private String studentEmailDomain;
    private String supervisorEmailDomain;
    private boolean studentEmailPrefixRestrictionEnabled = true;
    private String studentEmailPrefixRegex = "^IT(1[5-9]|[2-4][0-9]|50)[0-9]{6}$";
    private long otpExpirySeconds = 600;
    private long sessionExpirySeconds = 600;

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
        if (!domainRestrictionEnabled) {
            return true;
        }
        return inferRole(normalizedEmail) != null;
    }

    public boolean isEffectiveStudentEmailPrefixRestrictionEnabled() {
        return domainRestrictionEnabled
            && studentEmailPrefixRestrictionEnabled
            && hasStudentDomain()
            && studentEmailPrefixRegex != null
            && !studentEmailPrefixRegex.isBlank();
    }

    public boolean isStudentEmailPrefixAllowed(String normalizedEmail) {
        if (!isEffectiveStudentEmailPrefixRestrictionEnabled()) {
            return true;
        }

        if (!"STUDENT".equals(inferRole(normalizedEmail))) {
            return true;
        }

        int atIndex = normalizedEmail.indexOf('@');
        if (atIndex <= 0) {
            return false;
        }

        String localPart = normalizedEmail.substring(0, atIndex);
        return isStudentIdentifierAllowed(localPart);
    }

    public boolean isStudentIdentifierAllowed(String identifier) {
        if (!isEffectiveStudentEmailPrefixRestrictionEnabled()) {
            return true;
        }

        if (identifier == null || identifier.isBlank()) {
            return false;
        }

        try {
            return Pattern.compile(studentEmailPrefixRegex, Pattern.CASE_INSENSITIVE)
                .matcher(identifier)
                .matches();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }
}
