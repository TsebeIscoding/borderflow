package com.borderflow.auth;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Maps to `app_users` -- deliberately NOT a replicated table (see
 * db/migrations V6). Each site manages its own staff accounts
 * independently; there is no "global user directory," which matches
 * the offline-first premise the rest of the system is built on.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected AppUser() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }
}
