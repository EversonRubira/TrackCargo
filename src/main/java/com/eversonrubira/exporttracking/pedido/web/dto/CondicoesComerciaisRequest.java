package com.eversonrubira.exporttracking.pedido.web.dto;

import com.eversonrubira.exporttracking.pedido.FormaPagamento;
import com.eversonrubira.exporttracking.pedido.Incoterm;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Agrupa os termos do acordo comercial (preco, moeda, incoterm, forma
// de pagamento, percentual do parcial) separado dos dados de
// identificacao/rota do pedido - mudam juntos como "as condicoes
// negociadas", nao adiciona validacao nova, so organiza os mesmos 5
// campos do Builder num sub-objeto em vez de 14 campos soltos.
public record CondicoesComerciaisRequest(
        @NotNull(message = "precoAcordado e obrigatorio")
        BigDecimal precoAcordado,

        @Size(max = 3, message = "moeda deve ter no maximo 3 caracteres")
        String moeda,

        @NotNull(message = "incoterm e obrigatorio")
        Incoterm incoterm,

        @NotNull(message = "formaPagamento e obrigatoria")
        FormaPagamento formaPagamento,

        @NotNull(message = "percentualParcial e obrigatorio")
        BigDecimal percentualParcial
) {
}
