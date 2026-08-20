package com.borderflow.common;

import java.util.UUID;

public class TripNotFoundException extends RuntimeException {
    public TripNotFoundException(UUID tripId) {
        super("No trip found with id " + tripId);
    }
}
