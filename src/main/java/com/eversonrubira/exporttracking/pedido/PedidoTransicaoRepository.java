package com.eversonrubira.exporttracking.pedido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PedidoTransicaoRepository extends JpaRepository<PedidoTransicao, UUID> {

    List<PedidoTransicao> findByPedidoIdOrderByOcorridoEmAsc(UUID pedidoId);
}
