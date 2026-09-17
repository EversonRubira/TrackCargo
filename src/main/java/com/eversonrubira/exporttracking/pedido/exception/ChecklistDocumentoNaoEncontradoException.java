package com.eversonrubira.exporttracking.pedido.exception;

import com.eversonrubira.exporttracking.pedido.TipoDocumento;

public class ChecklistDocumentoNaoEncontradoException extends RuntimeException {
    public ChecklistDocumentoNaoEncontradoException(String numeroPedido, TipoDocumento tipo) {
        super("Documento " + tipo + " nao encontrado para o pedido: " + numeroPedido);
    }
}
