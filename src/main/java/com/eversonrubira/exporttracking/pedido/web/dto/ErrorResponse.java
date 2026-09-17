package com.eversonrubira.exporttracking.pedido.web.dto;

import com.eversonrubira.exporttracking.pedido.PedidoEstado;

// Corpo de erro padrao definido no SPEC.md: estadoAtual/estadoSolicitado
// só sao preenchidos pra TransicaoInvalidaException, os demais casos
// deixam os dois null.
public record ErrorResponse(
        String erro,
        String mensagem,
        PedidoEstado estadoAtual,
        PedidoEstado estadoSolicitado
) {
    public static ErrorResponse de(String erro, String mensagem) {
        return new ErrorResponse(erro, mensagem, null, null);
    }
}
