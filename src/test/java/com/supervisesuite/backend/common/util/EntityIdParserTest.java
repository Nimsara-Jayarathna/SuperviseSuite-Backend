package com.supervisesuite.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supervisesuite.backend.common.error.ValidationException;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityIdParserTest {

    @Test
    void parseOrNotFound_validUuid_returnsUuid() {
        UUID id = UUID.randomUUID();
        assertThat(EntityIdParser.parseOrNotFound(id.toString())).isEqualTo(id);
    }

    @Test
    void parseOrNotFound_invalidUuid_throwsEntityNotFound() {
        assertThatThrownBy(() -> EntityIdParser.parseOrNotFound("not-a-uuid"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void parseOrValidationError_invalidUuid_throwsValidationExceptionWithField() {
        assertThatThrownBy(() -> EntityIdParser.parseOrValidationError("not-a-uuid", "projectId"))
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getDetails()).hasSize(1);
                assertThat(ve.getDetails().get(0).getField()).isEqualTo("projectId");
            });
    }

    @Test
    void parseOrNull_blank_returnsNull() {
        assertThat(EntityIdParser.parseOrNull("   ", "linkedRepositoryId")).isNull();
    }

    @Test
    void parseOrNull_invalid_throwsValidationException() {
        assertThatThrownBy(() -> EntityIdParser.parseOrNull("not-a-uuid", "linkedRepositoryId"))
            .isInstanceOf(ValidationException.class);
    }
}

