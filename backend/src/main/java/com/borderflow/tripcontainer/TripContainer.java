package com.borderflow.tripcontainer;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to `trip_container` -- the join between Trip and Container the
 * design calls for (a Container physically travels as cargo within a
 * Trip's journey) but which had no code anywhere in this project until
 * now. Append-only, horizontally partitioned by originating site, same
 * shape as Handover -- deduped by its primary key
 * (trip_id, container_id) at the database level
 * (skip_duplicate_trip_container in the V4 migration under
 * db/migrations), so linking the same pair twice is a silent no-op,
 * not an error.
 */
@Entity
@Table(name = "trip_container")
@IdClass(TripContainerId.class)
public class TripContainer {

    @Id
    @Column(name = "trip_id")
    private UUID tripId;

    @Id
    @Column(name = "container_id")
    private UUID containerId;

    @Column(name = "site_id")
    private String siteId;

    @Column(name = "linked_at")
    private OffsetDateTime linkedAt;

    protected TripContainer() {
        // JPA
    }

    public TripContainer(UUID tripId, UUID containerId, String siteId) {
        this.tripId = tripId;
        this.containerId = containerId;
        this.siteId = siteId;
        this.linkedAt = OffsetDateTime.now();
    }

    public UUID getTripId() {
        return tripId;
    }

    public UUID getContainerId() {
        return containerId;
    }

    public String getSiteId() {
        return siteId;
    }

    public OffsetDateTime getLinkedAt() {
        return linkedAt;
    }
}
