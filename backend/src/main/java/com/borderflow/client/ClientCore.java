package com.borderflow.client;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Maps to `client_core` -- static Master data, single-leader like
 * every other Master fragment. Deliberately does NOT map
 * `client_contact` (the PII table) at all -- that table only exists at
 * Depot (see db/migrations/depot/V4) and is never given a JPA entity,
 * repository, or endpoint anywhere in this codebase. Building an API
 * around PII isn't something to do casually just because the schema
 * makes it possible.
 */
@Entity
@Table(name = "client_core")
public class ClientCore {

    @Id
    @Column(name = "client_id")
    private UUID clientId;

    private String name;

    protected ClientCore() {
        // JPA
    }

    public ClientCore(UUID clientId, String name) {
        this.clientId = clientId;
        this.name = name;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getName() {
        return name;
    }
}
