package com.eversonrubira.exporttracking.pedido.web.dto;

import com.eversonrubira.exporttracking.pedido.PedidoEstado;
import jakarta.validation.constraints.NotNull;

public record TransicionarRequest(
        @NotNull(message = "novoEstado e obrigatorio")
        PedidoEstado novoEstado
) {
}
