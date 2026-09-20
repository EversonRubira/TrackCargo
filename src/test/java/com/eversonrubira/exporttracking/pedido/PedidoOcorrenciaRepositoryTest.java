package com.eversonrubira.exporttracking.pedido;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest contra Postgres real (igual PedidoRepositoryTest) - o
// que importa provar aqui e a query derivada de verdade (filtro por
// tipo + tipoDocumento, ordenacao por ocorrido_em), nao um repository
// mockado que so ecoaria o que foi programado nele.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PedidoOcorrenciaRepositoryTest {

    @Autowired
    private PedidoOcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Pedido pedido;

    @Test
    void buscaRecusasDeUmDocumentoEmOrdemCronologicaIgnorandoOutrosTiposEDocumentos() throws InterruptedException {
        pedido = novoPedido("PO-OCORRENCIA-0001");
        entityManager.persistAndFlush(pedido);

        LocalDateTime primeiroEnvio = LocalDateTime.now().minusDays(2);
        entityManager.persistAndFlush(new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                "Invoice sem assinatura", TipoDocumento.INVOICE, primeiroEnvio));
        Thread.sleep(5);

        LocalDateTime segundoEnvio = LocalDateTime.now().minusDays(1);
        entityManager.persistAndFlush(new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                "Valor da invoice ainda divergente", TipoDocumento.INVOICE, segundoEnvio));

        // Ruido que a query precisa ignorar: outro tipo de ocorrencia
        // sobre o mesmo documento, e uma recusa de outro documento.
        entityManager.persistAndFlush(new PedidoOcorrencia(pedido, TipoOcorrencia.REABERTURA_DOCUMENTO,
                "Reabertura nao deve aparecer no historico de recusas"));
        entityManager.persistAndFlush(new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                "Recusa de outro documento, nao deve aparecer aqui", TipoDocumento.BL, LocalDateTime.now()));

        List<PedidoOcorrencia> recusas = ocorrenciaRepository
                .findByPedido_NumeroPedidoAndTipoAndTipoDocumentoOrderByOcorridoEmAsc(
                        "PO-OCORRENCIA-0001", TipoOcorrencia.RECUSA_DOCUMENTO, TipoDocumento.INVOICE);

        assertThat(recusas).hasSize(2);
        assertThat(recusas.get(0).getDescricao()).isEqualTo("Invoice sem assinatura");
        assertThat(recusas.get(0).getEnvioRecusadoEm()).isEqualTo(primeiroEnvio);
        assertThat(recusas.get(1).getDescricao()).isEqualTo("Valor da invoice ainda divergente");
        assertThat(recusas.get(1).getEnvioRecusadoEm()).isEqualTo(segundoEnvio);
        assertThat(recusas.get(0).getOcorridoEm()).isBefore(recusas.get(1).getOcorridoEm());
    }

    @Test
    void semRecusasRegistradasRetornaListaVazia() {
        pedido = novoPedido("PO-OCORRENCIA-0002");
        entityManager.persistAndFlush(pedido);

        List<PedidoOcorrencia> recusas = ocorrenciaRepository
                .findByPedido_NumeroPedidoAndTipoAndTipoDocumentoOrderByOcorridoEmAsc(
                        "PO-OCORRENCIA-0002", TipoOcorrencia.RECUSA_DOCUMENTO, TipoDocumento.INVOICE);

        assertThat(recusas).isEmpty();
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
