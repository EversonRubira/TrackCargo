package com.eversonrubira.exporttracking.pedido.exception;

public class PedidoNaoEncontradoException extends RuntimeException {

    private final String numeroPedido;

    public PedidoNaoEncontradoException(String numeroPedido) {
        super("Pedido nao encontrado: " + numeroPedido);
        this.numeroPedido = numeroPedido;
    }

    public String getNumeroPedido() {
        return numeroPedido;
    }
}
