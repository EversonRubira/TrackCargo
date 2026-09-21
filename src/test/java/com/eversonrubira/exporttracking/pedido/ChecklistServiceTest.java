package com.eversonrubira.exporttracking.pedido;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unitario puro: PedidoRepository/ChecklistDocumentoRepository ja sao
// interfaces (Spring Data), entao mockar aqui nao exige banco nem
// contexto Spring - roda em milissegundos, sem docker-compose de pe.
@ExtendWith(MockitoExtension.class)
class ChecklistServiceTest {

    @Mock
    private ChecklistDocumentoRepository checklistRepository;
    @Mock
    private PedidoTransicaoRepository transicaoRepository;
    @Mock
    private PedidoOcorrenciaRepository ocorrenciaRepository;

    @InjectMocks
    private ChecklistService checklistService;

    private Pedido pedido;
    private ChecklistDocumento invoice;
    private ChecklistDocumento packingList;
    private ChecklistDocumento bl;
    private ChecklistDocumento certificado;

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
                .moeda(Moeda.USD)
                .incoterm(Incoterm.CFR)
                .formaPagamento(FormaPagamento.TT_ANTECIPADO)
                .percentualParcial(new BigDecimal("30.00"))
                .build();

        invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        packingList = new ChecklistDocumento(pedido, TipoDocumento.PACKING_LIST);
        bl = new ChecklistDocumento(pedido, TipoDocumento.BL);
        certificado = new ChecklistDocumento(pedido, TipoDocumento.CERTIFICADO_SANITARIO);

        lenient().when(checklistRepository.findByPedidoId(any()))
                .thenReturn(List.of(invoice, packingList, bl, certificado));
    }

    @Test
    void primeiroEnvioDisparaDocumentacaoEnviada() {
        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.CRIADO);

        checklistService.enviar(invoice);

        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.DOCUMENTACAO_ENVIADA);
    }

    @Test
    void aceitarTodosOsItensDisparaDocumentacaoAceita() {
        checklistService.enviar(invoice);
        List.of(invoice, packingList, bl, certificado).forEach(d -> {
            checklistService.enviar(d);
            checklistService.aceitar(d);
        });

        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.DOCUMENTACAO_ACEITA);
    }

    @Test
    void reabrirAntesDoEmbarqueReverteEstadoDoPedido() {
        checklistService.enviar(invoice);
        List.of(invoice, packingList, bl, certificado).forEach(d -> {
            checklistService.enviar(d);
            checklistService.aceitar(d);
        });
        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.DOCUMENTACAO_ACEITA);

        checklistService.reabrirAposAceite(invoice, "Cliente pediu correcao do consignee na invoice");

        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.DOCUMENTACAO_ENVIADA);
        assertThat(invoice.getAceitoEm()).isNull();
        assertThat(invoice.getMotivoReabertura()).isNotBlank();
    }

    @Test
    void reabrirAposEmbarqueNaoReverteEstadoDoPedido() {
        checklistService.enviar(invoice);
        List.of(invoice, packingList, bl, certificado).forEach(d -> {
            checklistService.enviar(d);
            checklistService.aceitar(d);
        });
        pedido.aplicarTransicao(PedidoEstado.EMBARCADO);

        checklistService.reabrirAposAceite(invoice, "Consignee alterado apos desistencia do comprador original");

        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.EMBARCADO);
    }

    @Test
    void naoPermiteAceitarDocumentoNaoEnviado() {
        assertThatExceptionOfType(com.eversonrubira.exporttracking.pedido.exception.DocumentoNaoEnviadoException.class)
                .isThrownBy(() -> checklistService.aceitar(invoice));
    }

    @Test
    void naoPermiteReabrirDocumentoQueNuncaFoiAceito() {
        checklistService.enviar(invoice);

        assertThatExceptionOfType(com.eversonrubira.exporttracking.pedido.exception.DocumentoNaoAceitoException.class)
                .isThrownBy(() -> checklistService.reabrirAposAceite(invoice, "Tentativa invalida"));
    }

    @Test
    void recusarDocumentoEnviadoVoltaParaPendenteEGravaOcorrencia() {
        checklistService.enviar(invoice);
        LocalDateTime envioOriginal = invoice.getEnviadoEm();

        checklistService.recusar(invoice, "Invoice com valor divergente do contrato");

        assertThat(invoice.getEnviadoEm()).isNull();

        ArgumentCaptor<PedidoOcorrencia> captor = ArgumentCaptor.forClass(PedidoOcorrencia.class);
        verify(ocorrenciaRepository).save(captor.capture());
        PedidoOcorrencia ocorrencia = captor.getValue();
        assertThat(ocorrencia.getTipo()).isEqualTo(TipoOcorrencia.RECUSA_DOCUMENTO);
        assertThat(ocorrencia.getTipoDocumento()).isEqualTo(TipoDocumento.INVOICE);
        assertThat(ocorrencia.getDescricao()).isEqualTo("Invoice com valor divergente do contrato");
        assertThat(ocorrencia.getEnvioRecusadoEm()).isEqualTo(envioOriginal);
        assertThat(ocorrencia.getOcorridoEm()).isNotNull();
    }

    @Test
    void recusarAplicaTrimNoMotivoAntesDeSalvar() {
        checklistService.enviar(invoice);

        checklistService.recusar(invoice, "  Motivo com espacos nas pontas  ");

        ArgumentCaptor<PedidoOcorrencia> captor = ArgumentCaptor.forClass(PedidoOcorrencia.class);
        verify(ocorrenciaRepository).save(captor.capture());
        assertThat(captor.getValue().getDescricao()).isEqualTo("Motivo com espacos nas pontas");
    }

    @Test
    void recusarNaoAlteraEstadoDoPedidoMesmoComPedidoJaEmbarcado() {
        // Cenario real onde um documento fica "enviado, nao aceito" com o
        // pedido ja alem de DOCUMENTACAO_ACEITA: reaberto e reenviado apos
        // o embarque (reabrirAposAceite nao reverte estado depois de
        // EMBARCADO - so a ocorrencia e gravada).
        checklistService.enviar(invoice);
        List.of(invoice, packingList, bl, certificado).forEach(d -> {
            checklistService.enviar(d);
            checklistService.aceitar(d);
        });
        pedido.aplicarTransicao(PedidoEstado.EMBARCADO);
        checklistService.reabrirAposAceite(invoice, "Correcao necessaria pos-embarque");
        checklistService.enviar(invoice);

        checklistService.recusar(invoice, "Ainda incorreta apos reenvio");

        assertThat(pedido.getEstado()).isEqualTo(PedidoEstado.EMBARCADO);
    }

    @Test
    void naoPermiteRecusarDocumentoNaoEnviado() {
        assertThatExceptionOfType(com.eversonrubira.exporttracking.pedido.exception.DocumentoNaoEnviadoException.class)
                .isThrownBy(() -> checklistService.recusar(invoice, "Tentativa invalida"));
    }

    @Test
    void naoPermiteRecusarDocumentoJaAceito() {
        checklistService.enviar(invoice);
        checklistService.aceitar(invoice);

        assertThatExceptionOfType(com.eversonrubira.exporttracking.pedido.exception.DocumentoJaAceitoException.class)
                .isThrownBy(() -> checklistService.recusar(invoice, "Tentativa invalida"));
    }

    @Test
    void recusarDuasVezesGravaDuasOcorrenciasDistintasSemApagarAAnterior() {
        checklistService.enviar(invoice);
        checklistService.recusar(invoice, "Primeira recusa: invoice sem assinatura");
        checklistService.enviar(invoice);
        checklistService.recusar(invoice, "Segunda recusa: valor ainda divergente");

        ArgumentCaptor<PedidoOcorrencia> captor = ArgumentCaptor.forClass(PedidoOcorrencia.class);
        verify(ocorrenciaRepository, times(2)).save(captor.capture());
        List<PedidoOcorrencia> ocorrencias = captor.getAllValues();

        assertThat(ocorrencias).hasSize(2);
        assertThat(ocorrencias.get(0).getDescricao()).isEqualTo("Primeira recusa: invoice sem assinatura");
        assertThat(ocorrencias.get(1).getDescricao()).isEqualTo("Segunda recusa: valor ainda divergente");
        assertThat(!ocorrencias.get(1).getOcorridoEm().isBefore(ocorrencias.get(0).getOcorridoEm())).isTrue();
        assertThat(invoice.getEnviadoEm()).isNull();
    }
}
