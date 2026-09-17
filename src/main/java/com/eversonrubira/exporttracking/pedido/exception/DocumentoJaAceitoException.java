package com.eversonrubira.exporttracking.pedido.exception;

import com.eversonrubira.exporttracking.pedido.TipoDocumento;

public class DocumentoJaAceitoException extends RuntimeException {
    public DocumentoJaAceitoException(TipoDocumento tipo) {
        super("Documento ja aceito, use reabrirAposAceite: " + tipo);
    }
}
