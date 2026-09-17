package com.eversonrubira.exporttracking.pedido.exception;

import com.eversonrubira.exporttracking.pedido.PedidoEstado;

public class TransicaoInvalidaException extends RuntimeException {

    private final PedidoEstado estadoAtual;
    private final PedidoEstado estadoSolicitado;

    public TransicaoInvalidaException(PedidoEstado atual, PedidoEstado solicitado) {
        super("Transicao invalida de " + atual + " para " + solicitado);
        this.estadoAtual = atual;
        this.estadoSolicitado = solicitado;
    }

    public PedidoEstado getEstadoAtual() { return estadoAtual; }
    public PedidoEstado getEstadoSolicitado() { return estadoSolicitado; }
}
