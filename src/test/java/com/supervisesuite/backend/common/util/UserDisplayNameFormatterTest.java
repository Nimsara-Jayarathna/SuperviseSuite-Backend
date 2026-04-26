package com.supervisesuite.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.users.entity.User;
import org.junit.jupiter.api.Test;

class UserDisplayNameFormatterTest {

    @Test
    void format_firstAndLastName_returnsTrimmedFullName() {
        User user = new User();
        user.setFirstName("  Amal ");
        user.setLastName(" Perera  ");
        user.setEmail("amal.perera@university.ac.lk");

        assertThat(UserDisplayNameFormatter.format(user)).isEqualTo("Amal Perera");
    }

    @Test
    void format_blankNames_fallsBackToEmail() {
        User user = new User();
        user.setFirstName("   ");
        user.setLastName(null);
        user.setEmail("student@university.ac.lk");

        assertThat(UserDisplayNameFormatter.format(user)).isEqualTo("student@university.ac.lk");
    }

    @Test
    void format_nullUser_returnsEmptyString() {
        assertThat(UserDisplayNameFormatter.format(null)).isEqualTo("");
    }
}

