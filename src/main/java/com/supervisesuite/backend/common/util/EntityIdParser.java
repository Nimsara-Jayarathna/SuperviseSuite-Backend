package com.supervisesuite.backend.common.util;

import com.supervisesuite.backend.common.error.ValidationException;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;

/**
 * Centralized helpers for parsing UUID-backed entity identifiers from raw API inputs.
 *
 * <p>This utility exists to keep service/controller code consistent about which
 * exception type is thrown for a given failure mode.
 */
public final class EntityIdParser {

    private EntityIdParser() {
        // utility class
    }

    /**
     * Parses {@code rawId} as a UUID.
     *
     * @throws EntityNotFoundException when the input is not a valid UUID.
     */
    public static UUID parseOrNotFound(String rawId) {
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException();
        }
    }

    /**
     * Parses {@code rawId} as a UUID.
     *
     * @throws ValidationException when the input is not a valid UUID.
     */
    public static UUID parseOrValidationError(String rawId, String fieldName) {
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(fieldName, fieldName + " must be a valid UUID.");
        }
    }

    /**
     * Parses {@code rawId} as a UUID if present.
     *
     * @return {@code null} if {@code rawId} is {@code null} or blank
     * @throws ValidationException when the input is non-blank but not a valid UUID.
     */
    public static UUID parseOrNull(String rawId, String fieldName) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        return parseOrValidationError(rawId.trim(), fieldName);
    }
}

