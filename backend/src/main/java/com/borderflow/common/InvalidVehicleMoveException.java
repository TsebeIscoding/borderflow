package com.borderflow.common;

/** Same role as InvalidContainerMoveException, for vehicle relocation. */
public class InvalidVehicleMoveException extends RuntimeException {
    public InvalidVehicleMoveException(String message) {
        super(message);
    }
}
