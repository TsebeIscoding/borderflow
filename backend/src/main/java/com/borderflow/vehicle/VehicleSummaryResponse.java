package com.borderflow.vehicle;

import java.math.BigDecimal;
import java.util.UUID;

public record VehicleSummaryResponse(
        UUID vehicleId,
        String registrationNumber,
        BigDecimal capacity,
        String currentSiteId,
        String status,
        long lamportTs
) {
    static VehicleSummaryResponse from(VehicleProfile profile, VehicleAvailability availability) {
        return new VehicleSummaryResponse(
                profile.getVehicleId(),
                profile.getRegistrationNumber(),
                profile.getCapacity(),
                availability.getCurrentSiteId(),
                availability.getStatus(),
                availability.getLamportTs()
        );
    }
}
