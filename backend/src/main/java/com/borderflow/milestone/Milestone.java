package com.borderflow.milestone;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to `milestone` -- append-only event log, same shape as
 * Handover. Idempotency is enforced in the database
 * (skip_duplicate_milestone in the V4 migration under db/migrations),
 * not here -- see Handover's class javadoc for the same reasoning.
 */
@Entity
@Table(name = "milestone")
public class Milestone {

    @Id
    @Column(name = "milestone_id")
    private UUID milestoneId;

    @Column(name = "site_id")
    private String siteId;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "milestone_type")
    private String milestoneType;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    protected Milestone() {
        // JPA
    }

    public Milestone(UUID milestoneId, String siteId, UUID tripId, String milestoneType) {
        this.milestoneId = milestoneId;
        this.siteId = siteId;
        this.tripId = tripId;
        this.milestoneType = milestoneType;
        this.occurredAt = OffsetDateTime.now();
    }

    public UUID getMilestoneId() {
        return milestoneId;
    }

    public String getSiteId() {
        return siteId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getMilestoneType() {
        return milestoneType;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
