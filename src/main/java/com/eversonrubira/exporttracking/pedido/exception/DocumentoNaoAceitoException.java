package com.eversonrubira.exporttracking.pedido.exception;

import com.eversonrubira.exporttracking.pedido.TipoDocumento;

public class DocumentoNaoAceitoException extends RuntimeException {
    public DocumentoNaoAceitoException(TipoDocumento tipo) {
        super("Documento ainda nao foi aceito, nao ha o que reabrir: " + tipo);
    }
}
