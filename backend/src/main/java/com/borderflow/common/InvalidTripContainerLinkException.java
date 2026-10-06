package com.borderflow.common;

/** Thrown when linking/unlinking a Trip and a Container is attempted from a site that doesn't currently hold both of them -- see TripContainerService's class javadoc. */
public class InvalidTripContainerLinkException extends RuntimeException {
    public InvalidTripContainerLinkException(String message) {
        super(message);
    }
}
