package com.eversonrubira.exporttracking.pedido.exception;

import com.eversonrubira.exporttracking.pedido.TipoDocumento;

public class DocumentoJaAceitoException extends RuntimeException {

    private final TipoDocumento tipo;

    public DocumentoJaAceitoException(TipoDocumento tipo) {
        super("Documento ja aceito, use reabrirAposAceite: " + tipo);
        this.tipo = tipo;
    }

    public TipoDocumento getTipo() {
        return tipo;
    }
}
