package com.eversonrubira.exporttracking.pedido.exception;

import com.eversonrubira.exporttracking.pedido.TipoDocumento;

public class DocumentoNaoAceitoException extends RuntimeException {

    private final TipoDocumento tipo;

    public DocumentoNaoAceitoException(TipoDocumento tipo) {
        super("Documento ainda nao foi aceito, nao ha o que reabrir: " + tipo);
        this.tipo = tipo;
    }

    public TipoDocumento getTipo() {
        return tipo;
    }
}
