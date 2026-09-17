package com.eversonrubira.exporttracking.pedido.web.dto;

import com.eversonrubira.exporttracking.pedido.Pedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CriarPedidoRequest(
        @NotBlank(message = "numeroPedido e obrigatorio")
        @Size(max = 50)
        String numeroPedido,

        @NotBlank(message = "cliente e obrigatorio")
        @Size(max = 200)
        String cliente,

        @NotBlank(message = "consignee e obrigatorio")
        @Size(max = 200)
        String consignee,

        @NotBlank(message = "paisDestino e obrigatorio")
        @Size(max = 100)
        String paisDestino,

        @NotBlank(message = "portoOrigem e obrigatorio")
        @Size(max = 100)
        String portoOrigem,

        @NotBlank(message = "portoDestino e obrigatorio")
        @Size(max = 100)
        String portoDestino,

        @NotBlank(message = "produto e obrigatorio")
        @Size(max = 100)
        String produto,

        @NotNull(message = "quantidade e obrigatoria")
        BigDecimal quantidade,

        @NotBlank(message = "unidadeMedida e obrigatoria")
        @Size(max = 10)
        String unidadeMedida,

        @NotNull(message = "condicoesComerciais e obrigatorio")
        @Valid
        CondicoesComerciaisRequest condicoesComerciais
) {
    public Pedido paraPedido() {
        return Pedido.builder()
                .numeroPedido(numeroPedido)
                .cliente(cliente)
                .consignee(consignee)
                .paisDestino(paisDestino)
                .portoOrigem(portoOrigem)
                .portoDestino(portoDestino)
                .produto(produto)
                .quantidade(quantidade)
                .unidadeMedida(unidadeMedida)
                .precoAcordado(condicoesComerciais.precoAcordado())
                .moeda(condicoesComerciais.moeda())
                .incoterm(condicoesComerciais.incoterm())
                .formaPagamento(condicoesComerciais.formaPagamento())
                .percentualParcial(condicoesComerciais.percentualParcial())
                .build();
    }
}
