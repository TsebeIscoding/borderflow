package com.borderflow.trip;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to the `trip_master` table -- the Master fragment. Single-leader:
 * only ever written at the origin site (Depot in the current deployment).
 * This service treats it as strictly read-only, which matches how the
 * database itself is locked down -- non-origin sites have had
 * INSERT/UPDATE/DELETE revoked on this table for the `app_user` role
 * (see db/migrations/non-depot/V3, V5). There is deliberately no
 * setter/mutator here: if a bug ever tried to write through this entity
 * at a non-origin site, it should fail loudly at the database, not
 * silently no-op.
 */
@Entity
@Table(name = "trip_master")
public class TripMaster {

    @Id
    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "origin_site_id")
    private String originSiteId;

    @Column(name = "destination_site_id")
    private String destinationSiteId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected TripMaster() {
        // JPA
    }

    public TripMaster(UUID tripId, String originSiteId, String destinationSiteId) {
        this.tripId = tripId;
        this.originSiteId = originSiteId;
        this.destinationSiteId = destinationSiteId;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getOriginSiteId() {
        return originSiteId;
    }

    public String getDestinationSiteId() {
        return destinationSiteId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
