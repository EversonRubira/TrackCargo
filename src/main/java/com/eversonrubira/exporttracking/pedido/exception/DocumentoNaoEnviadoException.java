package com.eversonrubira.exporttracking.pedido.exception;

import com.eversonrubira.exporttracking.pedido.TipoDocumento;

public class DocumentoNaoEnviadoException extends RuntimeException {

    private final TipoDocumento tipo;

    public DocumentoNaoEnviadoException(TipoDocumento tipo) {
        super("Documento ainda nao foi enviado: " + tipo);
        this.tipo = tipo;
    }

    public TipoDocumento getTipo() {
        return tipo;
    }
}
