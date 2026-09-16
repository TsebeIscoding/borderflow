package com.borderflow.common;

/** Same role as InvalidVehicleMoveException, for driver relocation. */
public class InvalidDriverMoveException extends RuntimeException {
    public InvalidDriverMoveException(String message) {
        super(message);
    }
}
