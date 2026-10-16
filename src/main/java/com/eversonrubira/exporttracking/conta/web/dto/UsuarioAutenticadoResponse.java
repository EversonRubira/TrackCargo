package com.eversonrubira.exporttracking.conta.web.dto;

import com.eversonrubira.exporttracking.conta.Usuario;

// O minimo que o frontend precisa para saber quem esta logado. Nunca
// inclui a senha nem o hash.
public record UsuarioAutenticadoResponse(String nome, String email, String nomeEmpresa) {

    public static UsuarioAutenticadoResponse de(Usuario usuario) {
        return new UsuarioAutenticadoResponse(usuario.getNome(), usuario.getEmail(), usuario.getEmpresa().getNome());
    }
}
