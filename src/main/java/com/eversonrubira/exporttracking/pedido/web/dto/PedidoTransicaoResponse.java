package com.eversonrubira.exporttracking.pedido.web.dto;

import com.eversonrubira.exporttracking.pedido.PedidoEstado;
import com.eversonrubira.exporttracking.pedido.PedidoTransicao;

import java.time.LocalDateTime;

public record PedidoTransicaoResponse(
        PedidoEstado estadoAnterior,
        PedidoEstado estadoNovo,
        LocalDateTime ocorridoEm
) {
    public static PedidoTransicaoResponse de(PedidoTransicao transicao) {
        return new PedidoTransicaoResponse(
                transicao.getEstadoAnterior(),
                transicao.getEstadoNovo(),
                transicao.getOcorridoEm());
    }
}
