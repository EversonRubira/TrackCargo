package com.eversonrubira.exporttracking.conta;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Ponte entre a tabela usuario e o Spring Security: o
// DaoAuthenticationProvider (AutenticacaoConfig) chama isto com o email
// digitado no login e compara a senha com o hash devolvido. O "username"
// do Spring Security aqui e o email.
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        Usuario usuario = usuarioRepository.findByEmail(Usuario.normalizarEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Utilizador nao encontrado"));

        // Sem papeis (roles): o degrau 1 nao distingue papeis dentro da
        // empresa (spec, "Quadro de autorizacao").
        return User.withUsername(usuario.getEmail())
                .password(usuario.getSenhaHash())
                .authorities(List.of())
                .build();
    }
}
