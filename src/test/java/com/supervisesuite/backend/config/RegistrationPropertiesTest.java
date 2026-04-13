package com.supervisesuite.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationPropertiesTest {

    private RegistrationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RegistrationProperties();
        properties.setDomainRestrictionEnabled(true);
        properties.setStudentEmailDomain("@my.sliit.lk");
        properties.setSupervisorEmailDomain("@sliit.lk");
        properties.setStudentEmailPrefixRestrictionEnabled(true);
        properties.setStudentEmailPrefixRegex("^IT(1[5-9]|[2-4][0-9]|50)\\d{6}$");
    }

    @Test
    void studentPrefixRestriction_effectiveDisabled_whenDomainRestrictionDisabled() {
        properties.setDomainRestrictionEnabled(false);

        assertThat(properties.isEffectiveStudentEmailPrefixRestrictionEnabled()).isFalse();
        assertThat(properties.isStudentEmailPrefixAllowed("xx14123456@my.sliit.lk")).isTrue();
    }

    @Test
    void studentPrefixRestriction_acceptsValidStudentEmail_caseInsensitive() {
        assertThat(properties.isStudentEmailPrefixAllowed("IT24123456@my.sliit.lk")).isTrue();
        assertThat(properties.isStudentEmailPrefixAllowed("it24123456@my.sliit.lk")).isTrue();
    }

    @Test
    void studentPrefixRestriction_rejectsInvalidStudentPrefix() {
        assertThat(properties.isStudentEmailPrefixAllowed("xx24123456@my.sliit.lk")).isFalse();
        assertThat(properties.isStudentEmailPrefixAllowed("it14123456@my.sliit.lk")).isFalse();
        assertThat(properties.isStudentEmailPrefixAllowed("it51123456@my.sliit.lk")).isFalse();
    }

    @Test
    void studentPrefixRestriction_notAppliedToSupervisorEmail() {
        assertThat(properties.isStudentEmailPrefixAllowed("john.doe@sliit.lk")).isTrue();
    }
}
