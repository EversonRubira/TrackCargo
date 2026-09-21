package com.eversonrubira.exporttracking.pedido.web.dto;

import com.eversonrubira.exporttracking.pedido.FormaPagamento;
import com.eversonrubira.exporttracking.pedido.Incoterm;
import com.eversonrubira.exporttracking.pedido.Moeda;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// Agrupa os termos do acordo comercial (preco, moeda, incoterm, forma
// de pagamento, percentual do parcial) separado dos dados de
// identificacao/rota do pedido - mudam juntos como "as condicoes
// negociadas", nao adiciona validacao nova, so organiza os mesmos 5
// campos do Builder num sub-objeto em vez de 14 campos soltos.
//
// moeda continua opcional de proposito (sem @NotNull) - Pedido.Builder
// ja assume USD quando vier nulo, comportamento existente que esta
// mudanca nao altera. Valor invalido no JSON (ex: "Yen") nao chega a
// esta validacao - falha antes, na desserializacao do Jackson, tratada
// por GlobalExceptionHandler.tratarEnumInvalidoOuJsonMalformado().
public record CondicoesComerciaisRequest(
        @NotNull(message = "precoAcordado e obrigatorio")
        @Positive(message = "precoAcordado deve ser maior que zero")
        BigDecimal precoAcordado,

        Moeda moeda,

        @NotNull(message = "incoterm e obrigatorio")
        Incoterm incoterm,

        @NotNull(message = "formaPagamento e obrigatoria")
        FormaPagamento formaPagamento,

        @NotNull(message = "percentualParcial e obrigatorio")
        @DecimalMin(value = "0", message = "percentualParcial deve estar entre 0 e 100")
        @DecimalMax(value = "100", message = "percentualParcial deve estar entre 0 e 100")
        BigDecimal percentualParcial
) {
}
