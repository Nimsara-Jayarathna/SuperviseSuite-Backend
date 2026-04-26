package com.supervisesuite.backend.common.util;

import java.util.Locale;

/**
 * Stateless utility methods for normalizing user-supplied string values before
 * persistence or comparison.
 *
 * <p>All methods are {@code static} — this class is not meant to be instantiated.
 */
public final class NormalizationUtils {

    private NormalizationUtils() {
        // utility class — no instances
    }

    /**
     * Trims a string and returns {@code null} if it becomes blank.
     *
     * @param input raw user input; may be {@code null}
     * @return trimmed input, or {@code null} if blank
     */
    public static String trimToNull(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Returns {@code fallback} if {@code input} is {@code null} or blank; otherwise returns the trimmed input.
     */
    public static String defaultIfBlank(String input, String fallback) {
        String trimmed = trimToNull(input);
        return trimmed == null ? fallback : trimmed;
    }

    /**
     * Returns the canonical form of a student registration number.
     *
     * <p>Rules:
     * <ul>
     *   <li>Leading and trailing whitespace is removed.</li>
     *   <li>All letters are converted to uppercase using {@link Locale#ROOT} so that
     *       the result is independent of the JVM's default locale.</li>
     *   <li>Internal characters (digits, slashes, etc.) are not modified.</li>
     * </ul>
     *
     * <p>Examples:
     * <pre>
     *   " it24100400 " → "IT24100400"
     *   "bm22101412"   → "BM22101412"
     * </pre>
     *
     * @param input the raw registration number from user input; may be {@code null}
     * @return the normalized registration number, or {@code null} if {@code input} is {@code null}
     */
    public static String normalizeRegistrationNumber(String input) {
        String trimmed = trimToNull(input);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * Returns the canonical form of an email address.
     *
     * <p>Rules:
     * <ul>
     *   <li>Leading and trailing whitespace is removed.</li>
     *   <li>All letters are converted to lowercase using {@link Locale#ROOT} so that
     *       the result is independent of the JVM's default locale.</li>
     * </ul>
     *
     * <p>Examples:
     * <pre>
     *   " Alice@Example.COM " → "alice@example.com"
     *   "BOB@DOMAIN.ORG"      → "bob@domain.org"
     * </pre>
     *
     * @param input the raw email address from user input; may be {@code null}
     * @return the normalized email address, or {@code null} if {@code input} is {@code null}
     */
    public static String normalizeEmail(String input) {
        String trimmed = trimToNull(input);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
