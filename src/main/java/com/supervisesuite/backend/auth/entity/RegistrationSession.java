package com.supervisesuite.backend.auth.entity;

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

@Entity
@Table(name = "registration_sessions")
@Getter
@Setter
@NoArgsConstructor
public class RegistrationSession {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private String email;

    private String role;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    @Column(nullable = false)
    private Instant createdAt;
}
