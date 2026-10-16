package com.borderflow.common;

/**
 * A vehicle or driver cannot be assigned to (or released from) a trip right
 * now: the trip or the resource is held at another site, the trip is already
 * delivered, or the resource is already on another active trip. Maps to a 409.
 */
public class InvalidTripAssignmentException extends RuntimeException {
    public InvalidTripAssignmentException(String message) {
        super(message);
    }
}
