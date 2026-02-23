package com.supervisesuite.backend.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String role;

    // --- V2 auth fields ---

    /** BCrypt hash of the user's password. Nullable to support existing rows pre-auth. */
    private String passwordHash;

    private String firstName;

    private String lastName;

    @Column(nullable = false)
    private boolean isEmailVerified = false;
}
