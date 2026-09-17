package com.eversonrubira.exporttracking.pedido.web.dto;

import com.eversonrubira.exporttracking.pedido.ChecklistDocumento;
import com.eversonrubira.exporttracking.pedido.FormaPagamento;
import com.eversonrubira.exporttracking.pedido.Incoterm;
import com.eversonrubira.exporttracking.pedido.Pedido;
import com.eversonrubira.exporttracking.pedido.PedidoEstado;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        String numeroPedido,
        String cliente,
        String consignee,
        String paisDestino,
        String portoOrigem,
        String portoDestino,
        String produto,
        BigDecimal quantidade,
        String unidadeMedida,
        String ciaMaritima,
        String numeroContainer,
        BigDecimal precoAcordado,
        String moeda,
        Incoterm incoterm,
        FormaPagamento formaPagamento,
        BigDecimal percentualParcial,
        PedidoEstado estado,
        LocalDateTime pagamentoParcialConfirmadoEm,
        LocalDateTime pagamentoSaldoConfirmadoEm,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        List<ChecklistDocumentoResponse> checklist
) {
    public static PedidoResponse de(Pedido pedido, List<ChecklistDocumento> checklist) {
        return new PedidoResponse(
                pedido.getNumeroPedido(),
                pedido.getCliente(),
                pedido.getConsignee(),
                pedido.getPaisDestino(),
                pedido.getPortoOrigem(),
                pedido.getPortoDestino(),
                pedido.getProduto(),
                pedido.getQuantidade(),
                pedido.getUnidadeMedida(),
                pedido.getCiaMaritima(),
                pedido.getNumeroContainer(),
                pedido.getPrecoAcordado(),
                pedido.getMoeda(),
                pedido.getIncoterm(),
                pedido.getFormaPagamento(),
                pedido.getPercentualParcial(),
                pedido.getEstado(),
                pedido.getPagamentoParcialConfirmadoEm(),
                pedido.getPagamentoSaldoConfirmadoEm(),
                pedido.getCriadoEm(),
                pedido.getAtualizadoEm(),
                checklist.stream().map(ChecklistDocumentoResponse::de).toList());
    }
}
