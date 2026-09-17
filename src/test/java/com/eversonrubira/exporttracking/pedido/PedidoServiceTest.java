package com.eversonrubira.exporttracking.pedido;

import com.eversonrubira.exporttracking.pedido.exception.DocumentoAdicionalJaExisteException;
import com.eversonrubira.exporttracking.pedido.exception.TransicaoInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unitario puro, igual ao ChecklistServiceTest: repositories mockados,
// sem banco nem contexto Spring.
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ChecklistDocumentoRepository checklistRepository;
    @Mock
    private PedidoTransicaoRepository transicaoRepository;
    @Mock
    private PedidoOcorrenciaRepository ocorrenciaRepository;

    @InjectMocks
    private PedidoService pedidoService;

    private Pedido pedido;

    @BeforeEach
    void setUp() {
        pedido = Pedido.builder()
                .numeroPedido("PO-0001")
                .cliente("Cliente Teste")
                .consignee("Consignee Teste")
                .paisDestino("China")
                .portoOrigem("Porto de Santos")
                .portoDestino("Porto de Xangai")
                .produto("Carne bovina")
                .quantidade(new BigDecimal("20.000"))
                .unidadeMedida("TON")
                .precoAcordado(new BigDecimal("85000.00"))
                .moeda("USD")
                .incoterm(Incoterm.CFR)
                .formaPagamento(FormaPagamento.TT_ANTECIPADO)
                .percentualParcial(new BigDecimal("30.00"))
                .build();

        lenient().when(pedidoRepository.findByNumeroPedido("PO-0001")).thenReturn(Optional.of(pedido));
    }

    @Test
    void criarGeraChecklistZeradoETransicaoInicial() {
        when(pedidoRepository.save(pedido)).thenReturn(pedido);

        Pedido salvo = pedidoService.criar(pedido);

        assertThat(salvo).isSameAs(pedido);
        assertThat(salvo.getEstado()).isEqualTo(PedidoEstado.CRIADO);

        verify(checklistRepository, times(4)).save(any(ChecklistDocumento.class));

        ArgumentCaptor<PedidoTransicao> transicaoCaptor = ArgumentCaptor.forClass(PedidoTransicao.class);
        verify(transicaoRepository).save(transicaoCaptor.capture());
        PedidoTransicao transicaoInicial = transicaoCaptor.getValue();
        assertThat(transicaoInicial.getEstadoAnterior()).isNull();
        assertThat(transicaoInicial.getEstadoNovo()).isEqualTo(PedidoEstado.CRIADO);
    }

    @Test
    void transicionarComEstadoValidoAtualizaPedidoEGravaHistorico() {
        pedido.aplicarTransicao(PedidoEstado.DOCUMENTACAO_ACEITA);

        Pedido atualizado = pedidoService.transicionar("PO-0001", PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO);

        assertThat(atualizado.getEstado()).isEqualTo(PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO);

        ArgumentCaptor<PedidoTransicao> transicaoCaptor = ArgumentCaptor.forClass(PedidoTransicao.class);
        verify(transicaoRepository).save(transicaoCaptor.capture());
        PedidoTransicao transicao = transicaoCaptor.getValue();
        assertThat(transicao.getEstadoAnterior()).isEqualTo(PedidoEstado.DOCUMENTACAO_ACEITA);
        assertThat(transicao.getEstadoNovo()).isEqualTo(PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO);
    }

    @Test
    void transicionarComEstadoInvalidoLancaExcecaoENaoGravaHistorico() {
        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.CRIADO);

        assertThatExceptionOfType(TransicaoInvalidaException.class)
                .isThrownBy(() -> pedidoService.transicionar("PO-0001", PedidoEstado.EMBARCADO));

        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.CRIADO);
        verify(transicaoRepository, never()).save(any());
    }

    @Test
    void alterarConsigneeAtualizaPedidoEGeraOcorrencia() {
        Pedido atualizado = pedidoService.alterarConsignee(
                "PO-0001", "Novo Consignee", "Desistencia do comprador original");

        assertThat(atualizado.getConsignee()).isEqualTo("Novo Consignee");

        ArgumentCaptor<PedidoOcorrencia> ocorrenciaCaptor = ArgumentCaptor.forClass(PedidoOcorrencia.class);
        verify(ocorrenciaRepository).save(ocorrenciaCaptor.capture());
        PedidoOcorrencia ocorrencia = ocorrenciaCaptor.getValue();
        assertThat(ocorrencia.getTipo()).isEqualTo(TipoOcorrencia.ALTERACAO_DADOS_PEDIDO);
        assertThat(ocorrencia.getDescricao()).isEqualTo("Desistencia do comprador original");
    }

    @Test
    void confirmarPagamentoParcialMarcaDataETransicionaEstado() {
        pedido.aplicarTransicao(PedidoEstado.DOCUMENTACAO_ACEITA);

        Pedido atualizado = pedidoService.confirmarPagamentoParcial("PO-0001");

        assertThat(atualizado.getEstado()).isEqualTo(PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO);
        assertThat(atualizado.getPagamentoParcialConfirmadoEm()).isNotNull();

        ArgumentCaptor<PedidoTransicao> transicaoCaptor = ArgumentCaptor.forClass(PedidoTransicao.class);
        verify(transicaoRepository).save(transicaoCaptor.capture());
        assertThat(transicaoCaptor.getValue().getEstadoAnterior()).isEqualTo(PedidoEstado.DOCUMENTACAO_ACEITA);
        assertThat(transicaoCaptor.getValue().getEstadoNovo()).isEqualTo(PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO);
    }

    @Test
    void confirmarPagamentoParcialForaDeSequenciaLancaExcecao() {
        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.CRIADO);

        assertThatExceptionOfType(TransicaoInvalidaException.class)
                .isThrownBy(() -> pedidoService.confirmarPagamentoParcial("PO-0001"));

        assertThat(pedido.getPagamentoParcialConfirmadoEm()).isNull();
        verify(transicaoRepository, never()).save(any());
    }

    @Test
    void confirmarPagamentoSaldoMarcaDataETransicionaEstado() {
        pedido.aplicarTransicao(PedidoEstado.EMBARCADO);

        Pedido atualizado = pedidoService.confirmarPagamentoSaldo("PO-0001");

        assertThat(atualizado.getEstado()).isEqualTo(PedidoEstado.PAGAMENTO_SALDO_RECEBIDO);
        assertThat(atualizado.getPagamentoSaldoConfirmadoEm()).isNotNull();

        ArgumentCaptor<PedidoTransicao> transicaoCaptor = ArgumentCaptor.forClass(PedidoTransicao.class);
        verify(transicaoRepository).save(transicaoCaptor.capture());
        assertThat(transicaoCaptor.getValue().getEstadoAnterior()).isEqualTo(PedidoEstado.EMBARCADO);
        assertThat(transicaoCaptor.getValue().getEstadoNovo()).isEqualTo(PedidoEstado.PAGAMENTO_SALDO_RECEBIDO);
    }

    @Test
    void confirmarPagamentoSaldoSemEstarEmbarcadoLancaExcecao() {
        pedido.aplicarTransicao(PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO);

        assertThatExceptionOfType(TransicaoInvalidaException.class)
                .isThrownBy(() -> pedidoService.confirmarPagamentoSaldo("PO-0001"));

        assertThat(pedido.getPagamentoSaldoConfirmadoEm()).isNull();
        verify(transicaoRepository, never()).save(any());
    }

    @Test
    void buscarHistoricoDelegaParaORepositorioOrdenado() {
        PedidoTransicao transicao = new PedidoTransicao(pedido, null, PedidoEstado.CRIADO);
        when(transicaoRepository.findByPedidoIdOrderByOcorridoEmAsc(pedido.getId()))
                .thenReturn(List.of(transicao));

        List<PedidoTransicao> historico = pedidoService.buscarHistorico("PO-0001");

        assertThat(historico).containsExactly(transicao);
    }

    @Test
    void naoPermiteSegundoDocumentoAdicionalParaOMesmoPedido() {
        when(checklistRepository.existsByPedidoIdAndTipoDocumento(pedido.getId(), TipoDocumento.DOCUMENTO_ADICIONAL))
                .thenReturn(true);

        assertThatExceptionOfType(DocumentoAdicionalJaExisteException.class)
                .isThrownBy(() -> pedidoService.adicionarDocumentoAdicional("PO-0001", "Segundo extra"));

        verify(checklistRepository, never()).save(any());
    }
}
