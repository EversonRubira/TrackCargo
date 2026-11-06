package com.acme.shop;

public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(String message) {
        super(message);
    }
}
