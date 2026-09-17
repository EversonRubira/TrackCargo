package com.eversonrubira.exporttracking.pedido.exception;

import com.eversonrubira.exporttracking.pedido.PedidoEstado;

public class TransicaoInvalidaException extends RuntimeException {
    public TransicaoInvalidaException(PedidoEstado atual, PedidoEstado solicitado) {
        super("Transicao invalida de " + atual + " para " + solicitado);
    }
}
