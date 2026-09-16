package com.borderflow.common;

/** Same role as InvalidHandoverException, for container relocation -- see its javadoc. */
public class InvalidContainerMoveException extends RuntimeException {
    public InvalidContainerMoveException(String message) {
        super(message);
    }
}
