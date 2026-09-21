package com.eversonrubira.exporttracking.pedido.web;

import com.eversonrubira.exporttracking.pedido.ChecklistDocumento;
import com.eversonrubira.exporttracking.pedido.ChecklistService;
import com.eversonrubira.exporttracking.pedido.FormaPagamento;
import com.eversonrubira.exporttracking.pedido.Incoterm;
import com.eversonrubira.exporttracking.pedido.Moeda;
import com.eversonrubira.exporttracking.pedido.Pedido;
import com.eversonrubira.exporttracking.pedido.PedidoEstado;
import com.eversonrubira.exporttracking.pedido.PedidoSequenciaService;
import com.eversonrubira.exporttracking.pedido.PedidoService;
import com.eversonrubira.exporttracking.pedido.PedidoTransicao;
import com.eversonrubira.exporttracking.pedido.TipoDocumento;
import com.eversonrubira.exporttracking.pedido.exception.PedidoNaoEncontradoException;
import com.eversonrubira.exporttracking.pedido.exception.TransicaoInvalidaException;
import com.eversonrubira.exporttracking.pedido.pdf.PdfStatusService;
import com.eversonrubira.exporttracking.pedido.web.dto.CondicoesComerciaisRequest;
import com.eversonrubira.exporttracking.pedido.web.dto.CriarPedidoRequest;
import com.eversonrubira.exporttracking.pedido.web.dto.TransicionarRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest sobe so a camada web (controller + @RestControllerAdvice),
// PedidoService fica mockado - nao sobe contexto JPA/Postgres.
@WebMvcTest(PedidoController.class)
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private PdfStatusService pdfStatusService;

    @MockitoBean
    private PedidoSequenciaService pedidoSequenciaService;

    @MockitoBean
    private ChecklistService checklistService;

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
                .moeda(Moeda.USD)
                .incoterm(Incoterm.CFR)
                .formaPagamento(FormaPagamento.TT_ANTECIPADO)
                .percentualParcial(new BigDecimal("30.00"))
                .build();
    }

    private CriarPedidoRequest requestValido() {
        return requestComCondicoesComerciais(
                new BigDecimal("20.000"), new BigDecimal("85000.00"), new BigDecimal("30.00"));
    }

    private CriarPedidoRequest requestComCondicoesComerciais(
            BigDecimal quantidade, BigDecimal precoAcordado, BigDecimal percentualParcial) {
        return new CriarPedidoRequest(
                "PO-0001", "Cliente Teste", "Consignee Teste", "China",
                "Porto de Santos", "Porto de Xangai", "Carne bovina",
                quantidade, "TON",
                new CondicoesComerciaisRequest(
                        precoAcordado, Moeda.USD, Incoterm.CFR,
                        FormaPagamento.TT_ANTECIPADO, percentualParcial));
    }

    // moeda/incoterm invalidos nao chegam a este tipo Java (o enum nao
    // aceita valor fora dele) - precisam de JSON literal pra simular o
    // que um cliente HTTP de verdade manda.
    private String corpoComMoeda(String moeda) {
        return """
                {
                  "numeroPedido": "PO-0001", "cliente": "Cliente Teste", "consignee": "Consignee Teste",
                  "paisDestino": "China", "portoOrigem": "Porto de Santos", "portoDestino": "Porto de Xangai",
                  "produto": "Carne bovina", "quantidade": 20.000, "unidadeMedida": "TON",
                  "condicoesComerciais": {
                    "precoAcordado": 85000.00, "moeda": "%s", "incoterm": "CFR",
                    "formaPagamento": "TT_ANTECIPADO", "percentualParcial": 30.00
                  }
                }
                """.formatted(moeda);
    }

    private String corpoComIncoterm(String incoterm) {
        return """
                {
                  "numeroPedido": "PO-0001", "cliente": "Cliente Teste", "consignee": "Consignee Teste",
                  "paisDestino": "China", "portoOrigem": "Porto de Santos", "portoDestino": "Porto de Xangai",
                  "produto": "Carne bovina", "quantidade": 20.000, "unidadeMedida": "TON",
                  "condicoesComerciais": {
                    "precoAcordado": 85000.00, "moeda": "USD", "incoterm": "%s",
                    "formaPagamento": "TT_ANTECIPADO", "percentualParcial": 30.00
                  }
                }
                """.formatted(incoterm);
    }

    @Test
    void criarRetorna201ComPedidoCriado() throws Exception {
        when(pedidoService.criar(any(Pedido.class))).thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroPedido").value("PO-0001"))
                .andExpect(jsonPath("$.estado").value("CRIADO"))
                .andExpect(jsonPath("$.checklist").isArray());
    }

    @Test
    void proximoNumeroRetorna200ComSugestaoDoService() throws Exception {
        when(pedidoSequenciaService.sugerirProximoNumero()).thenReturn("00007/2026");

        mockMvc.perform(get("/pedidos/proximo-numero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroPedidoSugerido").value("00007/2026"));
    }

    @Test
    void criarComCamposObrigatoriosFaltandoRetorna400() throws Exception {
        String jsonInvalido = """
                {"numeroPedido": "", "cliente": "Cliente Teste"}
                """;

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void criarComMoedaInvalidaRetorna400ComMensagemPorCampoListandoOEnum() throws Exception {
        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComMoeda("Yen")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.mensagem").value(
                        "condicoesComerciais.moeda: valores aceitos sao USD, EUR e BRL"));
    }

    @Test
    void criarComMoedaMinusculaInvalidaRetorna400() throws Exception {
        // Deserializacao de enum e case-sensitive por padrao - "usd"
        // minusculo tambem nao bate com nenhuma constante de Moeda.
        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComMoeda("usd")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void criarComIncotermInvalidoRetorna400ComMensagemPorCampoListandoOEnum() throws Exception {
        // Mesmo handler (HttpMessageNotReadableException) cobrindo um enum
        // que ja existia antes desta feature - prova que a solucao nao e
        // hardcoded pra Moeda.
        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComIncoterm("XXX")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.mensagem").value(
                        "condicoesComerciais.incoterm: valores aceitos sao "
                                + "EXW, FCA, FAS, FOB, CFR, CIF, CPT, CIP, DAP, DPU e DDP"));
    }

    @Test
    void criarComJsonMalformadoRetorna400ComMensagemGenericaSemTextoInternoDoJackson() throws Exception {
        String jsonQuebrado = """
                { "numeroPedido": "PO-0001", "cliente":
                """;

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonQuebrado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.mensagem").value("Corpo da requisicao invalido ou mal formado"));
    }

    @Test
    void criarComQuantidadeZeroRetorna400() throws Exception {
        CriarPedidoRequest request = requestComCondicoesComerciais(
                BigDecimal.ZERO, new BigDecimal("85000.00"), new BigDecimal("30.00"));

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void criarComQuantidadeNegativaRetorna400() throws Exception {
        CriarPedidoRequest request = requestComCondicoesComerciais(
                new BigDecimal("-1"), new BigDecimal("85000.00"), new BigDecimal("30.00"));

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void criarComPrecoAcordadoZeroRetorna400() throws Exception {
        CriarPedidoRequest request = requestComCondicoesComerciais(
                new BigDecimal("20.000"), BigDecimal.ZERO, new BigDecimal("30.00"));

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void criarComPrecoAcordadoNegativoRetorna400() throws Exception {
        CriarPedidoRequest request = requestComCondicoesComerciais(
                new BigDecimal("20.000"), new BigDecimal("-1"), new BigDecimal("30.00"));

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void criarComPercentualParcialZeroRetorna201() throws Exception {
        CriarPedidoRequest request = requestComCondicoesComerciais(
                new BigDecimal("20.000"), new BigDecimal("85000.00"), BigDecimal.ZERO);
        when(pedidoService.criar(any(Pedido.class))).thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void criarComPercentualParcialCemRetorna201() throws Exception {
        CriarPedidoRequest request = requestComCondicoesComerciais(
                new BigDecimal("20.000"), new BigDecimal("85000.00"), new BigDecimal("100"));
        when(pedidoService.criar(any(Pedido.class))).thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void criarComPercentualParcialNegativoRetorna400() throws Exception {
        CriarPedidoRequest request = requestComCondicoesComerciais(
                new BigDecimal("20.000"), new BigDecimal("85000.00"), new BigDecimal("-1"));

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void criarComPercentualParcialAcimaDeCemRetorna400() throws Exception {
        CriarPedidoRequest request = requestComCondicoesComerciais(
                new BigDecimal("20.000"), new BigDecimal("85000.00"), new BigDecimal("101"));

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void buscarPorNumeroRetorna200ComPedidoEChecklist() throws Exception {
        when(pedidoService.buscarPorNumero("PO-0001")).thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(
                List.of(new ChecklistDocumento(pedido, TipoDocumento.INVOICE)));

        mockMvc.perform(get("/pedidos/PO-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroPedido").value("PO-0001"))
                .andExpect(jsonPath("$.consignee").value("Consignee Teste"))
                .andExpect(jsonPath("$.checklist[0].tipoDocumento").value("INVOICE"));
    }

    @Test
    void buscarPorNumeroInexistenteRetorna404() throws Exception {
        when(pedidoService.buscarPorNumero("PO-9999"))
                .thenThrow(new PedidoNaoEncontradoException("PO-9999"));

        mockMvc.perform(get("/pedidos/PO-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("PEDIDO_NAO_ENCONTRADO"));
    }

    @Test
    void transicionarComEstadoValidoRetorna200() throws Exception {
        // Pedido.aplicarTransicao() e pacote-privado de proposito (so
        // PedidoService/ChecklistService mudam estado) - este teste, no
        // pacote .web, so pode verificar que o controller devolve 200 e
        // repassa o pedido que o service (mockado) retornou; a mudanca de
        // estado em si ja e coberta por PedidoServiceTest.
        when(pedidoService.transicionar(eq("PO-0001"), eq(PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO)))
                .thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(patch("/pedidos/PO-0001/transicionar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransicionarRequest(PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroPedido").value("PO-0001"));
    }

    @Test
    void transicionarComEstadoInvalidoRetorna409() throws Exception {
        when(pedidoService.transicionar("PO-0001", PedidoEstado.EMBARCADO))
                .thenThrow(new TransicaoInvalidaException(PedidoEstado.CRIADO, PedidoEstado.EMBARCADO));

        mockMvc.perform(patch("/pedidos/PO-0001/transicionar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransicionarRequest(PedidoEstado.EMBARCADO))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("TRANSICAO_INVALIDA"))
                .andExpect(jsonPath("$.estadoAtual").value("CRIADO"))
                .andExpect(jsonPath("$.estadoSolicitado").value("EMBARCADO"));
    }

    @Test
    void transicionarSemNovoEstadoRetorna400() throws Exception {
        mockMvc.perform(patch("/pedidos/PO-0001/transicionar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void confirmarPagamentoParcialRetorna200() throws Exception {
        when(pedidoService.confirmarPagamentoParcial("PO-0001")).thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(post("/pedidos/PO-0001/pagamento-parcial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroPedido").value("PO-0001"));
    }

    @Test
    void confirmarPagamentoParcialForaDeSequenciaRetorna409() throws Exception {
        when(pedidoService.confirmarPagamentoParcial("PO-0001"))
                .thenThrow(new TransicaoInvalidaException(PedidoEstado.CRIADO, PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO));

        mockMvc.perform(post("/pedidos/PO-0001/pagamento-parcial"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("TRANSICAO_INVALIDA"));
    }

    @Test
    void confirmarPagamentoSaldoRetorna200() throws Exception {
        when(pedidoService.confirmarPagamentoSaldo("PO-0001")).thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(post("/pedidos/PO-0001/pagamento-saldo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroPedido").value("PO-0001"));
    }

    @Test
    void confirmarPagamentoSaldoSemEstarEmbarcadoRetorna409() throws Exception {
        when(pedidoService.confirmarPagamentoSaldo("PO-0001"))
                .thenThrow(new TransicaoInvalidaException(PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO, PedidoEstado.PAGAMENTO_SALDO_RECEBIDO));

        mockMvc.perform(post("/pedidos/PO-0001/pagamento-saldo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("TRANSICAO_INVALIDA"));
    }

    @Test
    void historicoVazioRetorna200ComListaVazia() throws Exception {
        when(pedidoService.buscarHistorico("PO-0001")).thenReturn(List.of());

        mockMvc.perform(get("/pedidos/PO-0001/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void historicoComTransicoesRetornaListaOrdenada() throws Exception {
        // PedidoTransicao so guarda o par de estados que recebeu no
        // construtor - nao depende do estado atual de `pedido`, entao nao
        // precisa (nem pode, aplicarTransicao e pacote-privado) mudar o
        // estado do Pedido de fixture aqui.
        PedidoTransicao criacao = new PedidoTransicao(pedido, null, PedidoEstado.CRIADO);
        PedidoTransicao envio = new PedidoTransicao(pedido, PedidoEstado.CRIADO, PedidoEstado.DOCUMENTACAO_ENVIADA);
        when(pedidoService.buscarHistorico("PO-0001")).thenReturn(List.of(criacao, envio));

        mockMvc.perform(get("/pedidos/PO-0001/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].estadoAnterior").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].estadoNovo").value("CRIADO"))
                .andExpect(jsonPath("$[1].estadoAnterior").value("CRIADO"))
                .andExpect(jsonPath("$[1].estadoNovo").value("DOCUMENTACAO_ENVIADA"));
    }

    @Test
    void historicoDePedidoInexistenteRetorna404() throws Exception {
        when(pedidoService.buscarHistorico("PO-9999"))
                .thenThrow(new PedidoNaoEncontradoException("PO-9999"));

        mockMvc.perform(get("/pedidos/PO-9999/historico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("PEDIDO_NAO_ENCONTRADO"));
    }

    @Test
    void listarSemFiltroRetorna200ComTodosOsPedidos() throws Exception {
        when(pedidoService.listar(null)).thenReturn(List.of(pedido));
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(get("/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numeroPedido").value("PO-0001"));
    }

    @Test
    void listarComFiltroEstadoRetorna200SoComOsFiltrados() throws Exception {
        when(pedidoService.listar(PedidoEstado.EMBARCADO)).thenReturn(List.of(pedido));
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(get("/pedidos").param("estado", "EMBARCADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(pedidoService).listar(PedidoEstado.EMBARCADO);
    }

    @Test
    void atualizarLogisticaComSoCiaMaritimaAtualizaSoEsseCampo() throws Exception {
        when(pedidoService.atualizarDadosLogisticos("PO-0001", "Maersk", null)).thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(patch("/pedidos/PO-0001/logistica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "ciaMaritima": "Maersk" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroPedido").value("PO-0001"));

        verify(pedidoService).atualizarDadosLogisticos(eq("PO-0001"), eq("Maersk"), isNull());
    }

    @Test
    void atualizarLogisticaComSoNumeroContainerAtualizaSoEsseCampo() throws Exception {
        when(pedidoService.atualizarDadosLogisticos("PO-0001", null, "MSCU1234566")).thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(patch("/pedidos/PO-0001/logistica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "numeroContainer": "MSCU1234566" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroPedido").value("PO-0001"));

        verify(pedidoService).atualizarDadosLogisticos(eq("PO-0001"), isNull(), eq("MSCU1234566"));
    }

    @Test
    void atualizarLogisticaComOsDoisCamposAtualizaAmbos() throws Exception {
        when(pedidoService.atualizarDadosLogisticos("PO-0001", "Maersk", "MSCU1234566")).thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(patch("/pedidos/PO-0001/logistica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "ciaMaritima": "Maersk", "numeroContainer": "MSCU1234566" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroPedido").value("PO-0001"));

        verify(pedidoService).atualizarDadosLogisticos("PO-0001", "Maersk", "MSCU1234566");
    }

    @Test
    void atualizarLogisticaComNumeroContainerInvalidoRetorna400SemChamarOService() throws Exception {
        mockMvc.perform(patch("/pedidos/PO-0001/logistica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "numeroContainer": "Plastico" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));

        verify(pedidoService, org.mockito.Mockito.never()).atualizarDadosLogisticos(any(), any(), any());
    }

    @Test
    void atualizarLogisticaComDigitoVerificadorErradoRetorna400() throws Exception {
        mockMvc.perform(patch("/pedidos/PO-0001/logistica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "numeroContainer": "MSCU1234569" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void atualizarLogisticaComNumeroContainerMinusculoEspacadoPassaNaValidacao() throws Exception {
        // A anotacao normaliza internamente antes de checar - minuscula,
        // com espaco e hifen precisa passar do mesmo jeito que a forma
        // ja normalizada. O valor de verdade gravado (normalizado) so e
        // confirmado onde o service real roda (PedidoServiceTest/E2E),
        // ja que aqui pedidoService e mock.
        when(pedidoService.atualizarDadosLogisticos(eq("PO-0001"), isNull(), any())).thenReturn(pedido);
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());

        mockMvc.perform(patch("/pedidos/PO-0001/logistica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "numeroContainer": "mscu 123456-6" }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void atualizarLogisticaDePedidoInexistenteRetorna404() throws Exception {
        when(pedidoService.atualizarDadosLogisticos("PO-9999", "Maersk", null))
                .thenThrow(new PedidoNaoEncontradoException("PO-9999"));

        mockMvc.perform(patch("/pedidos/PO-9999/logistica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "ciaMaritima": "Maersk" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("PEDIDO_NAO_ENCONTRADO"));
    }

    @Test
    void gerarPdfStatusRetorna200ComContentTypePdf() throws Exception {
        byte[] pdfFalso = {1, 2, 3};
        when(pedidoService.buscarPorNumero("PO-0001")).thenReturn(pedido);
        when(pedidoService.buscarHistorico("PO-0001")).thenReturn(List.of());
        when(pedidoService.buscarChecklist("PO-0001")).thenReturn(List.of());
        when(checklistService.buscarRecusasPorDocumento("PO-0001")).thenReturn(Map.of());
        when(pdfStatusService.gerar(pedido, List.of(), List.of(), Map.of())).thenReturn(pdfFalso);

        mockMvc.perform(get("/pedidos/PO-0001/status.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void gerarPdfStatusDePedidoInexistenteRetorna404() throws Exception {
        when(pedidoService.buscarPorNumero("PO-9999"))
                .thenThrow(new PedidoNaoEncontradoException("PO-9999"));

        mockMvc.perform(get("/pedidos/PO-9999/status.pdf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("PEDIDO_NAO_ENCONTRADO"));
    }
}
