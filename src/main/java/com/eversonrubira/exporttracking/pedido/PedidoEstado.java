package com.eversonrubira.exporttracking.pedido;

import java.util.Map;
import java.util.Set;

public enum PedidoEstado {
    CRIADO,
    DOCUMENTACAO_ENVIADA,
    DOCUMENTACAO_ACEITA,
    PAGAMENTO_PARCIAL_RECEBIDO,
    EMBARCADO,
    PAGAMENTO_SALDO_RECEBIDO,
    DOCUMENTOS_ORIGINAIS_ENVIADOS,
    ENTREGUE,
    CANCELADO;

    // DOCUMENTACAO_ENVIADA e DOCUMENTACAO_ACEITA nao aparecem como destino
    // em nenhum conjunto abaixo: sao alcancados so pelo efeito colateral
    // do checklist (Fase 3), nunca por chamada manual ao /transicionar.
    private static final Map<PedidoEstado, Set<PedidoEstado>> TRANSICOES_MANUAIS = Map.of(
            CRIADO, Set.of(CANCELADO),
            DOCUMENTACAO_ENVIADA, Set.of(CANCELADO),
            DOCUMENTACAO_ACEITA, Set.of(PAGAMENTO_PARCIAL_RECEBIDO, CANCELADO),
            PAGAMENTO_PARCIAL_RECEBIDO, Set.of(EMBARCADO, CANCELADO),
            EMBARCADO, Set.of(PAGAMENTO_SALDO_RECEBIDO),
            PAGAMENTO_SALDO_RECEBIDO, Set.of(DOCUMENTOS_ORIGINAIS_ENVIADOS),
            DOCUMENTOS_ORIGINAIS_ENVIADOS, Set.of(ENTREGUE),
            ENTREGUE, Set.of(),
            CANCELADO, Set.of()
    );

    public boolean podeTransicionarManualmentePara(PedidoEstado novoEstado) {
        return TRANSICOES_MANUAIS.getOrDefault(this, Set.of()).contains(novoEstado);
    }
}
