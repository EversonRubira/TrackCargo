package com.eversonrubira.exporttracking.pedido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChecklistDocumentoRepository extends JpaRepository<ChecklistDocumento, UUID> {

    List<ChecklistDocumento> findByPedidoId(UUID pedidoId);

    boolean existsByPedidoIdAndTipoDocumento(UUID pedidoId, TipoDocumento tipoDocumento);
}
