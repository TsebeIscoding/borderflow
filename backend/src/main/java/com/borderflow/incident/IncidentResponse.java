package com.borderflow.incident;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IncidentResponse(
        UUID incidentId,
        String siteId,
        UUID tripId,
        String description,
        OffsetDateTime occurredAt
) {
    static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getIncidentId(),
                incident.getSiteId(),
                incident.getTripId(),
                incident.getDescription(),
                incident.getOccurredAt()
        );
    }
}
