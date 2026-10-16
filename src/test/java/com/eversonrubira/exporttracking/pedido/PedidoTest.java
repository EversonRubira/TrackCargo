package com.eversonrubira.exporttracking.pedido;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

// Unitario puro da entidade: so a regra do Builder, sem banco.
class PedidoTest {

    // Sem dona o pedido nem chega a existir em memoria: falha no build(),
    // antes de qualquer INSERT (o NOT NULL da V7 e a segunda barreira).
    @Test
    void builderRecusaPedidoSemEmpresaExportadora() {
        Pedido.Builder semDona = Pedido.builder()
                .numeroPedido("PO-0001")
                .cliente("Cliente Teste")
                .consignee("Consignee Teste")
                .paisDestino("China")
                .portoOrigem("Porto de Santos")
                .portoDestino("Porto de Xangai")
                .produto("Carne bovina")
                .quantidade(new BigDecimal("20.000"))
                .unidadeMedida("TON")
                .precoAcordado(new BigDecimal("85000.00"))
                .moeda(Moeda.USD)
                .incoterm(Incoterm.CFR)
                .formaPagamento(FormaPagamento.TT_ANTECIPADO)
                .percentualParcial(new BigDecimal("30.00"));

        assertThatNullPointerException()
                .isThrownBy(semDona::build)
                .withMessage("empresaExportadoraId e obrigatorio");
    }
}
