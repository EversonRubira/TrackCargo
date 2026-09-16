package com.eversonrubira.exporttracking.pedido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChecklistDocumentoRepository extends JpaRepository<ChecklistDocumento, UUID> {
}
