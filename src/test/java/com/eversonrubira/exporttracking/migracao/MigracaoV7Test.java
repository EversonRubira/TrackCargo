package com.eversonrubira.exporttracking.migracao;

import com.eversonrubira.exporttracking.conta.Empresa;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

// Testa a V7 sobre dados que ja existiam, como na producao: migra um
// schema separado so ate a V6, grava pedidos "antigos", aplica a V7 e
// confere que nenhum se perdeu e todos ficaram com a empresa padrao.
// Schema proprio para nao tocar no "public" que os outros testes usam.
//
// NOT_SUPPORTED desliga a transacao que o @JdbcTest abriria: o Flyway
// usa outra conexao e nao veria INSERTs ainda nao confirmados.
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MigracaoV7Test {

    private static final String SCHEMA = "teste_migracao_v7";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void apagarSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void v7PassaPedidosExistentesParaEmpresaPadraoSemPerderNenhum() {
        migrarAte("6");
        inserirPedidoAntigo("PO-ANTIGO-1");
        inserirPedidoAntigo("PO-ANTIGO-2");
        inserirPedidoAntigo("PO-ANTIGO-3");
        assertThat(contarPedidos()).isEqualTo(3);

        migrarAte("7");

        assertThat(contarPedidos()).isEqualTo(3);
        List<UUID> donas = jdbc.queryForList(
                "SELECT empresa_exportadora_id FROM " + SCHEMA + ".pedido", UUID.class);
        assertThat(donas).hasSize(3).containsOnly(Empresa.ID_PADRAO);

        String anulavel = jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = ? AND table_name = 'pedido' AND column_name = 'empresa_exportadora_id'
                """, String.class, SCHEMA);
        assertThat(anulavel).isEqualTo("NO");

        // Chave estrangeira: uma dona que nao existe e recusada pela base.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> inserirPedidoComDona("PO-NOVO-1", UUID.randomUUID()));
    }

    private void migrarAte(String versao) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .target(versao)
                .load()
                .migrate();
    }

    private long contarPedidos() {
        return jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".pedido", Long.class);
    }

    // Colunas da tabela pedido tal como estava na V6 (sem dona).
    private void inserirPedidoAntigo(String numero) {
        jdbc.update("""
                INSERT INTO %s.pedido (id, numero_pedido, cliente, consignee, pais_destino, porto_origem,
                    porto_destino, produto, quantidade, unidade_medida, preco_acordado, moeda, incoterm,
                    forma_pagamento, percentual_parcial, estado, criado_em, atualizado_em)
                VALUES (?, ?, 'Cliente', 'Consignee', 'China', 'Santos', 'Xangai', 'Soja', 1000, 'TON',
                    50000, 'USD', 'CFR', 'TT_ANTECIPADO', 30, 'CRIADO', now(), now())
                """.formatted(SCHEMA), UUID.randomUUID(), numero);
    }

    private void inserirPedidoComDona(String numero, UUID empresaId) {
        jdbc.update("""
                INSERT INTO %s.pedido (id, numero_pedido, cliente, consignee, pais_destino, porto_origem,
                    porto_destino, produto, quantidade, unidade_medida, preco_acordado, moeda, incoterm,
                    forma_pagamento, percentual_parcial, estado, criado_em, atualizado_em, empresa_exportadora_id)
                VALUES (?, ?, 'Cliente', 'Consignee', 'China', 'Santos', 'Xangai', 'Soja', 1000, 'TON',
                    50000, 'USD', 'CFR', 'TT_ANTECIPADO', 30, 'CRIADO', now(), now(), ?)
                """.formatted(SCHEMA), UUID.randomUUID(), numero, empresaId);
    }
}
