package com.borderflow.handover;

import java.util.UUID;

public record HandoverResponse(
        UUID eventId,
        UUID tripId,
        String fromSiteId,
        String toSiteId,
        String newStatus,
        long lamportTs
) {
}
