package com.eversonrubira.exporttracking.pedido.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReabrirDocumentoRequest(
        @NotBlank(message = "motivo e obrigatorio")
        @Size(max = 500)
        String motivo
) {
}
