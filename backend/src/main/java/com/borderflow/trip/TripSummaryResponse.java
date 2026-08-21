package com.borderflow.trip;

import java.util.UUID;

/**
 * Combined Master + State view of a trip, for read endpoints. Built from
 * a join across two tables that are physically separate fragments (see
 * db/migrations/*/V1) -- the read side is free to recombine them, only
 * writes have to respect the fragment boundary.
 */
public record TripSummaryResponse(
        UUID tripId,
        String originSiteId,
        String destinationSiteId,
        String currentSiteId,
        String status,
        long lamportTs
) {
    static TripSummaryResponse from(TripMaster master, TripState state) {
        return new TripSummaryResponse(
                master.getTripId(),
                master.getOriginSiteId(),
                master.getDestinationSiteId(),
                state.getCurrentSiteId(),
                state.getStatus(),
                state.getLamportTs()
        );
    }
}
