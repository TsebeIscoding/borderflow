package com.borderflow.common;

/**
 * Thrown when a delete is rejected by the database's own foreign key
 * constraints (e.g. deleting a Client that still has Consignments
 * referencing it). Caught around the delete call and rethrown as this
 * clean exception rather than letting a raw
 * DataIntegrityViolationException reach the caller as an unhandled
 * 500 -- see the *DeletionService classes for where this is caught.
 */
public class EntityInUseException extends RuntimeException {
    public EntityInUseException(String message) {
        super(message);
    }
}
