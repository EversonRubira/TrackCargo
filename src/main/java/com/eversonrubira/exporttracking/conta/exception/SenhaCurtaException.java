package com.eversonrubira.exporttracking.conta.exception;

public class SenhaCurtaException extends RuntimeException {

    private final int minimo;

    public SenhaCurtaException(int minimo) {
        super("Senha deve ter pelo menos " + minimo + " caracteres");
        this.minimo = minimo;
    }

    public int getMinimo() {
        return minimo;
    }
}
