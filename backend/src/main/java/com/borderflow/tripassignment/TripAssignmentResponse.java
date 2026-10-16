package com.borderflow.tripassignment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TripAssignmentResponse(
        UUID assignmentId,
        String siteId,
        UUID tripId,
        String resourceType,
        UUID resourceId,
        String action,
        OffsetDateTime occurredAt
) {
    static TripAssignmentResponse from(TripAssignment assignment) {
        return new TripAssignmentResponse(
                assignment.getAssignmentId(),
                assignment.getSiteId(),
                assignment.getTripId(),
                assignment.getResourceType(),
                assignment.getResourceId(),
                assignment.getAction(),
                assignment.getOccurredAt()
        );
    }
}
