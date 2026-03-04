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
        if (input == null) {
            return null;
        }
        return input.trim().toUpperCase(Locale.ROOT);
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
        if (input == null) {
            return null;
        }
        return input.trim().toLowerCase(Locale.ROOT);
    }
}
