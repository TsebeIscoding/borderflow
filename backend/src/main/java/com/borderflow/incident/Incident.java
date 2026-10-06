package com.borderflow.incident;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps to `incident` -- same shape as Milestone, see its class javadoc. */
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "site_id")
    private String siteId;

    @Column(name = "trip_id")
    private UUID tripId;

    private String description;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    protected Incident() {
        // JPA
    }

    public Incident(UUID incidentId, String siteId, UUID tripId, String description) {
        this.incidentId = incidentId;
        this.siteId = siteId;
        this.tripId = tripId;
        this.description = description;
        this.occurredAt = OffsetDateTime.now();
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getSiteId() {
        return siteId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
