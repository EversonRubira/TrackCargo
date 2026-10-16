package com.eversonrubira.exporttracking.conta.exception;

public class SenhaLongaException extends RuntimeException {

    private final int maximoBytes;

    public SenhaLongaException(int maximoBytes) {
        super("Senha deve ter no maximo " + maximoBytes + " bytes");
        this.maximoBytes = maximoBytes;
    }

    public int getMaximoBytes() {
        return maximoBytes;
    }
}
