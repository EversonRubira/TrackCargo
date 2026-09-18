package com.eversonrubira.exporttracking.pedido.web.dto;

import jakarta.validation.constraints.Size;

// Os dois campos sao opcionais de proposito - PATCH parcial. Cada um
// so e atualizado se vier preenchido; omitir um campo deixa o valor
// atual do pedido como esta (ver Pedido.aplicarDadosLogisticos()).
public record AtualizarLogisticaRequest(
        @Size(max = 100)
        String ciaMaritima,

        @Size(max = 30)
        String numeroContainer
) {
}
