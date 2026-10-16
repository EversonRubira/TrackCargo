package com.eversonrubira.exporttracking.conta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @DataJpaTest contra Postgres real (igual PedidoRepositoryTest): o que
// importa provar e a migracao V6 de verdade, em especial o indice unico
// sobre LOWER(email), que um banco em memoria nao reproduziria igual.
// Cada teste roda numa transacao desfeita no fim (padrao do @DataJpaTest).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void gravaUtilizadorLigadoAEmpresaEEncontraPeloEmail() {
        Empresa empresa = entityManager.persistAndFlush(new Empresa("Exportadora Repo"));
        entityManager.persistAndFlush(new Usuario(empresa, "Ana", "ana.repo@exportadora.com", "{bcrypt}hash"));
        entityManager.clear();

        Optional<Usuario> encontrado = usuarioRepository.findByEmail("ana.repo@exportadora.com");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getEmpresa().getNome()).isEqualTo("Exportadora Repo");
        assertThat(encontrado.get().getCriadoEm()).isNotNull();
        assertThat(encontrado.get().getAtualizadoEm()).isNotNull();
        assertThat(usuarioRepository.existsByEmail("ana.repo@exportadora.com")).isTrue();
        assertThat(usuarioRepository.existsByEmail("outra@exportadora.com")).isFalse();
    }

    // INSERT direto em SQL de proposito, passando por cima do
    // Usuario.normalizarEmail: prova que a propria base recusa o mesmo
    // email com maiusculas diferentes, mesmo que a aplicacao falhe.
    @Test
    void baseRecusaMesmoEmailComMaiusculasDiferentes() {
        Empresa empresa = entityManager.persistAndFlush(new Empresa("Exportadora Repo"));
        inserirUsuarioPorSql(empresa.getId(), "bia.repo@exportadora.com");

        assertThatThrownBy(() -> inserirUsuarioPorSql(empresa.getId(), "BIA.Repo@Exportadora.com"))
                .hasMessageContaining("uk_usuario_email");
    }

    private void inserirUsuarioPorSql(UUID empresaId, String email) {
        entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO usuario (id, empresa_id, nome, email, senha_hash, criado_em, atualizado_em)
                        VALUES (?, ?, 'Bia', ?, '{bcrypt}hash', now(), now())
                        """)
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, empresaId)
                .setParameter(3, email)
                .executeUpdate();
    }
}
