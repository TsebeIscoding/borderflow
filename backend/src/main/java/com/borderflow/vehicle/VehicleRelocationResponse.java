package com.borderflow.vehicle;

import java.util.UUID;

public record VehicleRelocationResponse(
        UUID vehicleId,
        String fromSiteId,
        String toSiteId,
        long lamportTs
) {
}
