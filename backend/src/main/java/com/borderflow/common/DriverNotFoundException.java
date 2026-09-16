package com.borderflow.common;

import java.util.UUID;

public class DriverNotFoundException extends RuntimeException {
    public DriverNotFoundException(UUID driverId) {
        super("No driver found with id " + driverId);
    }
}
