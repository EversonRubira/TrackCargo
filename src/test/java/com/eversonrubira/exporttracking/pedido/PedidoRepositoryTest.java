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
        return Pedido.builder()
                .numeroPedido(numeroPedido)
                .cliente("Cliente Teste")
                .consignee("Consignee Teste")
                .paisDestino("Argentina")
                .portoOrigem("Porto de Santos")
                .portoDestino("Buenos Aires")
                .produto("Soja")
                .quantidade(new BigDecimal("1000.00"))
                .unidadeMedida("TON")
                .precoAcordado(new BigDecimal("50000.00"))
                .moeda("USD")
                .incoterm(Incoterm.CFR)
                .formaPagamento(FormaPagamento.TT_ANTECIPADO)
                .percentualParcial(new BigDecimal("30.00"))
                .build();
    }
}
