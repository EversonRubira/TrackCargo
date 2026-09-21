package com.eversonrubira.exporttracking.pedido.web.dto;

import com.eversonrubira.exporttracking.pedido.validacao.NumeroContainerIso6346;
import jakarta.validation.constraints.Size;

// Os dois campos sao opcionais de proposito - PATCH parcial. Cada um
// so e atualizado se vier preenchido; omitir um campo deixa o valor
// atual do pedido como esta (ver Pedido.aplicarDadosLogisticos()).
//
// numeroContainer: @NumeroContainerIso6346 valida a forma NORMALIZADA
// (a anotacao normaliza internamente antes de checar formato/digito
// verificador - ver NumeroContainerIso6346Validator), mas quem grava o
// valor normalizado de fato e PedidoService.atualizarDadosLogisticos(),
// chamando a mesma classe pura Iso6346. @Size(max=30) continua como
// limite defensivo sobre o valor bruto (antes de normalizar).
public record AtualizarLogisticaRequest(
        @Size(max = 100)
        String ciaMaritima,

        @Size(max = 30)
        @NumeroContainerIso6346
        String numeroContainer
) {
}
