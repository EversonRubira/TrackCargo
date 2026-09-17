package com.eversonrubira.exporttracking.pedido;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
                .moeda("USD")
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
}
