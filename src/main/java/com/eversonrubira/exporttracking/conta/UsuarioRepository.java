package com.eversonrubira.exporttracking.conta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// Os dois metodos esperam o email ja normalizado (Usuario.normalizarEmail):
// a coluna so guarda minusculas, entao a comparacao exata basta.
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
