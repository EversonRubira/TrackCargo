package com.eversonrubira.exporttracking.pedido.exception;

public class PedidoNaoEncontradoException extends RuntimeException {
    public PedidoNaoEncontradoException(String numeroPedido) {
        super("Pedido nao encontrado: " + numeroPedido);
    }
}
