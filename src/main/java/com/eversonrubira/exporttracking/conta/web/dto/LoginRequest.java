package com.eversonrubira.exporttracking.conta.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email e obrigatorio")
        String email,

        @NotBlank(message = "senha e obrigatoria")
        String senha
) {
}
