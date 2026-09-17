package com.eversonrubira.exporttracking.pedido.exception;

import com.eversonrubira.exporttracking.pedido.TipoDocumento;

public class DocumentoNaoEnviadoException extends RuntimeException {
    public DocumentoNaoEnviadoException(TipoDocumento tipo) {
        super("Documento ainda nao foi enviado: " + tipo);
    }
}
