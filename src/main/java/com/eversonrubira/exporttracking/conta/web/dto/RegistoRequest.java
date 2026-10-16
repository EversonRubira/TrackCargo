package com.eversonrubira.exporttracking.conta.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Tamanho da senha nao e validado aqui, e no ContaService (SENHA_CURTA,
// SENHA_LONGA): e regra de conta, nao de formato do pedido HTTP, e assim
// tem codigo de erro proprio em vez de cair no TAMANHO_MAXIMO generico.
public record RegistoRequest(
        @NotBlank(message = "nomeEmpresa e obrigatorio")
        @Size(max = 200, message = "nomeEmpresa deve ter no maximo 200 caracteres")
        String nomeEmpresa,

        @NotBlank(message = "nomeUsuario e obrigatorio")
        @Size(max = 200, message = "nomeUsuario deve ter no maximo 200 caracteres")
        String nomeUsuario,

        @NotBlank(message = "email e obrigatorio")
        @Size(max = 200, message = "email deve ter no maximo 200 caracteres")
        String email,

        @NotBlank(message = "senha e obrigatoria")
        String senha
) {
}
