package com.eversonrubira.exporttracking.pedido.web.dto;

import com.eversonrubira.exporttracking.pedido.ChecklistDocumento;
import com.eversonrubira.exporttracking.pedido.TipoDocumento;

import java.time.LocalDateTime;

public record ChecklistDocumentoResponse(
        TipoDocumento tipoDocumento,
        String descricao,
        LocalDateTime enviadoEm,
        LocalDateTime aceitoEm,
        LocalDateTime reabertoEm,
        String motivoReabertura
) {
    public static ChecklistDocumentoResponse de(ChecklistDocumento documento) {
        return new ChecklistDocumentoResponse(
                documento.getTipoDocumento(),
                documento.getDescricao(),
                documento.getEnviadoEm(),
                documento.getAceitoEm(),
                documento.getReabertoEm(),
                documento.getMotivoReabertura());
    }
}
