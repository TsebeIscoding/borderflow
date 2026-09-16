package com.borderflow.driver;

import java.util.UUID;

public record DriverRelocationResponse(
        UUID driverId,
        String fromSiteId,
        String toSiteId,
        long lamportTs
) {
}
