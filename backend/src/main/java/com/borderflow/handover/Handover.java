package com.borderflow.handover;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to the `handover` table -- an append-only event log, horizontally
 * partitioned by originating site (each site writes its own handover
 * events locally; they replicate outward, they are never edited).
 * Idempotency is enforced in the database, not here -- the
 * `skip_duplicate_handover` trigger silently absorbs a re-inserted
 * `eventId` (see the V1 migration under db/migrations). That means this insert
 * can be safely retried by a client without needing idempotency-key
 * bookkeeping in the service layer.
 */
@Entity
@Table(name = "handover")
public class Handover {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "from_site_id")
    private String fromSiteId;

    @Column(name = "to_site_id")
    private String toSiteId;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    protected Handover() {
        // JPA
    }

    public Handover(UUID eventId, UUID tripId, String fromSiteId, String toSiteId, String verifiedBy) {
        this.eventId = eventId;
        this.tripId = tripId;
        this.fromSiteId = fromSiteId;
        this.toSiteId = toSiteId;
        this.verifiedBy = verifiedBy;
        this.occurredAt = OffsetDateTime.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getFromSiteId() {
        return fromSiteId;
    }

    public String getToSiteId() {
        return toSiteId;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
