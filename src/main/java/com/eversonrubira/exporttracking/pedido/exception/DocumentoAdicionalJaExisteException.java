package com.eversonrubira.exporttracking.pedido.exception;

public class DocumentoAdicionalJaExisteException extends RuntimeException {
    public DocumentoAdicionalJaExisteException(String numeroPedido) {
        super("Pedido ja tem um documento adicional cadastrado: " + numeroPedido);
    }
}
