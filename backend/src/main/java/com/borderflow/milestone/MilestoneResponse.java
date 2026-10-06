package com.borderflow.milestone;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MilestoneResponse(
        UUID milestoneId,
        String siteId,
        UUID tripId,
        String milestoneType,
        OffsetDateTime occurredAt
) {
    static MilestoneResponse from(Milestone milestone) {
        return new MilestoneResponse(
                milestone.getMilestoneId(),
                milestone.getSiteId(),
                milestone.getTripId(),
                milestone.getMilestoneType(),
                milestone.getOccurredAt()
        );
    }
}
