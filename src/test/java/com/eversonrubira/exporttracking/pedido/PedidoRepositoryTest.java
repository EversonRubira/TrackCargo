package com.eversonrubira.exporttracking.pedido;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PedidoRepositoryTest {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deveEncontrarPedidoPeloNumeroDoPedido() {
        Pedido pedido = novoPedido("PO-0001");
        entityManager.persistAndFlush(pedido);

        Optional<Pedido> encontrado = pedidoRepository.findByNumeroPedido("PO-0001");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getCliente()).isEqualTo(pedido.getCliente());
    }

    @Test
    void naoDeveEncontrarPedidoParaNumeroInexistente() {
        Optional<Pedido> encontrado = pedidoRepository.findByNumeroPedido("PO-INEXISTENTE");

        assertThat(encontrado).isEmpty();
    }

    @Test
    void naoDevePermitirDoisPedidosComMesmoNumero() {
        entityManager.persistAndFlush(novoPedido("PO-0002"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> pedidoRepository.saveAndFlush(novoPedido("PO-0002")));
    }

    private Pedido novoPedido(String numeroPedido) {
        return new Pedido(
                numeroPedido,
                "Cliente Teste",
                "Argentina",
                "Porto de Santos",
                "Buenos Aires",
                "Soja",
                new BigDecimal("1000.00"),
                "TON",
                new BigDecimal("50000.00"),
                "USD",
                Incoterm.CFR,
                FormaPagamento.TT_ANTECIPADO,
                new BigDecimal("30.00")
        );
    }
}
