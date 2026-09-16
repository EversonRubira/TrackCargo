package com.eversonrubira.exporttracking.pedido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    Optional<Pedido> findByNumeroInvoice(String numeroInvoice);
}
