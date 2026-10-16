package com.eversonrubira.exporttracking.conta.exception;

public class EmailJaRegistadoException extends RuntimeException {

    public EmailJaRegistadoException(String email) {
        super("Email ja registado: " + email);
    }
}
