package com.acme.export.consignment;

public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(String message) {
        super(message);
    }
}
