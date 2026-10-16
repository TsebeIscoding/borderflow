package com.borderflow.common;

/** A consignment request whose fields are individually valid but inconsistent with each other. Maps to a 400. */
public class InvalidConsignmentDetailsException extends RuntimeException {
    public InvalidConsignmentDetailsException(String message) {
        super(message);
    }
}
