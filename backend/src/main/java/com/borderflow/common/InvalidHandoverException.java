package com.borderflow.common;

/**
 * Thrown for a handover request that fails a business rule -- e.g.
 * handing off a trip that's already Delivered, or a site trying to hand
 * off a trip it doesn't currently hold. Deliberately distinct from a
 * conflict caught by the database's resolve_trip_state_conflict trigger:
 * that trigger catches races between two sites writing concurrently;
 * this exception catches a single site's request that was simply wrong
 * on its face, before it ever reaches the database.
 */
public class InvalidHandoverException extends RuntimeException {
    public InvalidHandoverException(String message) {
        super(message);
    }
}
