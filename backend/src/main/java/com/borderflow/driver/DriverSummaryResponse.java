package com.borderflow.driver;

import java.util.UUID;

public record DriverSummaryResponse(
        UUID driverId,
        String name,
        String licenseNumber,
        String phone,
        String currentSiteId,
        String status,
        long lamportTs
) {
    static DriverSummaryResponse from(DriverProfile profile, DriverAvailability availability) {
        return new DriverSummaryResponse(
                profile.getDriverId(),
                profile.getName(),
                profile.getLicenseNumber(),
                profile.getPhone(),
                availability.getCurrentSiteId(),
                availability.getStatus(),
                availability.getLamportTs()
        );
    }
}
