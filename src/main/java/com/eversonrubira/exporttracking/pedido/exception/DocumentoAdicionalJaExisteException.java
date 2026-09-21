package com.eversonrubira.exporttracking.pedido.exception;

public class DocumentoAdicionalJaExisteException extends RuntimeException {

    private final String numeroPedido;

    public DocumentoAdicionalJaExisteException(String numeroPedido) {
        super("Pedido ja tem um documento adicional cadastrado: " + numeroPedido);
        this.numeroPedido = numeroPedido;
    }

    public String getNumeroPedido() {
        return numeroPedido;
    }
}
