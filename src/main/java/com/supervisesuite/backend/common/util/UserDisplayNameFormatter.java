package com.supervisesuite.backend.common.util;

import com.supervisesuite.backend.users.entity.User;

/**
 * Formats user-friendly display names consistently across the application.
 */
public final class UserDisplayNameFormatter {

    private UserDisplayNameFormatter() {
        // utility class
    }

    /**
     * Returns a display name for a user.
     *
     * <p>Rules:
     * <ul>
     *   <li>Uses {@code firstName + " " + lastName} when present, trimming whitespace.</li>
     *   <li>Falls back to email if the combined name is blank.</li>
     *   <li>Never returns {@code null}.</li>
     * </ul>
     */
    public static String format(User user) {
        if (user == null) {
            return "";
        }

        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }

        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        return email.isEmpty() ? "" : email;
    }
}

