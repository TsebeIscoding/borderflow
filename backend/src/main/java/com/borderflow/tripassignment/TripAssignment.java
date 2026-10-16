package com.borderflow.tripassignment;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to `trip_assignment` -- an append-only Event log (V12), deduped by
 * primary key like Handover, Milestone and Incident. One row each time a
 * vehicle or driver is assigned to, or released from, a trip. Never updated
 * or deleted, so a driver can appear on many trips over time and a mid-trip
 * swap leaves a record. The trip's CURRENT vehicle and driver live in
 * trip_state; this is the history.
 */
@Entity
@Table(name = "trip_assignment")
public class TripAssignment {

    @Id
    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "site_id")
    private String siteId;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    private String action;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    protected TripAssignment() {
        // JPA
    }

    public TripAssignment(UUID assignmentId, String siteId, UUID tripId, String resourceType, UUID resourceId, String action) {
        this.assignmentId = assignmentId;
        this.siteId = siteId;
        this.tripId = tripId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.action = action;
        this.occurredAt = OffsetDateTime.now();
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public String getSiteId() {
        return siteId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getAction() {
        return action;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
