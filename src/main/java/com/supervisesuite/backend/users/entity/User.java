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

/**
 * JPA entity representing an application user.
 *
 * <p>Maps to the {@code users} table. Schema is managed by Flyway:
 * <ul>
 *   <li>V1 — base table ({@code id}, {@code email}, {@code role})</li>
 *   <li>V2 — auth fields ({@code password_hash}, {@code first_name}, {@code last_name})</li>
 *   <li>V3 — {@code registration_number}</li>
 * </ul>
 *
 * <p>Lombok generates all getters, setters, and a no-arg constructor.
 * No builder is used; the service layer populates fields via setters.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    /** Auto-generated UUID primary key. */
    @Id
    @GeneratedValue
    private UUID id;

    /** Timestamp when the record was created. Set once on registration; never updated. */
    @Column(nullable = false)
    private Instant createdAt;

    /** Timestamp of the last update. {@code null} until the record is first modified. */
    private Instant updatedAt;

    /** Unique email address used as the login identifier. */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Application role assigned to this user.
     * Constrained by a DB CHECK to {@code 'SUPERVISOR'} or {@code 'STUDENT'} (V2).
     *
     * @see com.supervisesuite.backend.common.constants.Roles
     */
    @Column(nullable = false)
    private String role;

    // --- V2 auth fields ---

    /**
     * BCrypt hash of the user's password.
     * Nullable at the DB level to support rows created before auth was introduced.
     * The application layer enforces a non-null value on registration.
     */
    private String passwordHash;

    /** Student or supervisor's given name. */
    private String firstName;

    /** Student or supervisor's family name. */
    private String lastName;

    // --- V3 fields ---

    /**
     * Institutional registration / student number.
     * Format: 2 uppercase letters followed by 8 digits (e.g. IT24100400).
     * Stored in normalized uppercase form via {@link com.supervisesuite.backend.common.util.NormalizationUtils}.
     * Nullable at the DB level to support pre-seeded supervisor rows.
     * The application layer enforces a non-null value on student registration.
     */
    @Column(name = "registration_number", unique = true, length = 20)
    private String registrationNumber;
}
