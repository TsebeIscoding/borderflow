package com.borderflow.container;

import java.util.UUID;

/** Combined Master + State view, same pattern as TripSummaryResponse. */
public record ContainerSummaryResponse(
        UUID containerId,
        String containerNumber,
        UUID consignmentId,
        String size,
        String currentSiteId,
        String status,
        long lamportTs
) {
    static ContainerSummaryResponse from(ContainerMaster master, ContainerState state) {
        return new ContainerSummaryResponse(
                master.getContainerId(),
                master.getContainerNumber(),
                master.getConsignmentId(),
                master.getSize(),
                state.getCurrentSiteId(),
                state.getStatus(),
                state.getLamportTs()
        );
    }
}
