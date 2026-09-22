package com.eversonrubira.exporttracking.pedido.exception;

import com.eversonrubira.exporttracking.pedido.TipoDocumento;

public class ChecklistDocumentoNaoEncontradoException extends RuntimeException {

    private final String numeroPedido;
    private final TipoDocumento tipo;

    public ChecklistDocumentoNaoEncontradoException(String numeroPedido, TipoDocumento tipo) {
        super("Documento " + tipo + " nao encontrado para o pedido: " + numeroPedido);
        this.numeroPedido = numeroPedido;
        this.tipo = tipo;
    }

    public String getNumeroPedido() {
        return numeroPedido;
    }

    public TipoDocumento getTipo() {
        return tipo;
    }
}
