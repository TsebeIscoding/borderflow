package com.borderflow.common;

import java.util.UUID;

public class VehicleNotFoundException extends RuntimeException {
    public VehicleNotFoundException(UUID vehicleId) {
        super("No vehicle found with id " + vehicleId);
    }
}
