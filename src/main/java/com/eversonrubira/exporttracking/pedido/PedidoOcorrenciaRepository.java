package com.eversonrubira.exporttracking.pedido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PedidoOcorrenciaRepository extends JpaRepository<PedidoOcorrencia, UUID> {

    // Historico de recusas de um documento especifico, em ordem - mesmo
    // depois de reenviado/aceito, as recusas passadas continuam aqui
    // (pedido_ocorrencia nunca e sobrescrita pelo reenvio). E o que o
    // PDF de status (tarefa futura) vai consumir.
    List<PedidoOcorrencia> findByPedido_NumeroPedidoAndTipoAndTipoDocumentoOrderByOcorridoEmAsc(
            String numeroPedido, TipoOcorrencia tipo, TipoDocumento tipoDocumento);
}
