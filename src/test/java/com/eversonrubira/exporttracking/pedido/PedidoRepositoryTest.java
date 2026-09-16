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
    void deveEncontrarPedidoPeloNumeroDaInvoice() {
        Pedido pedido = novoPedido("INV-0001");
        entityManager.persistAndFlush(pedido);

        Optional<Pedido> encontrado = pedidoRepository.findByNumeroInvoice("INV-0001");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getCliente()).isEqualTo(pedido.getCliente());
    }

    @Test
    void naoDeveEncontrarPedidoParaInvoiceInexistente() {
        Optional<Pedido> encontrado = pedidoRepository.findByNumeroInvoice("INV-INEXISTENTE");

        assertThat(encontrado).isEmpty();
    }

    @Test
    void naoDevePermitirDoisPedidosComMesmoNumeroDeInvoice() {
        entityManager.persistAndFlush(novoPedido("INV-0002"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> pedidoRepository.saveAndFlush(novoPedido("INV-0002")));
    }

    private Pedido novoPedido(String numeroInvoice) {
        return new Pedido(
                numeroInvoice,
                "Cliente Teste",
                "Argentina",
                "Buenos Aires",
                "Soja",
                new BigDecimal("1000.00"),
                "TON",
                new BigDecimal("50000.00"),
                "USD"
        );
    }
}
