package com.borderflow.container;

import java.util.UUID;

public record ContainerRelocationResponse(
        UUID containerId,
        String fromSiteId,
        String toSiteId,
        String newStatus,
        long lamportTs
) {
}
