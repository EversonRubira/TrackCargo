package com.eversonrubira.exporttracking.pedido.web;

import com.eversonrubira.exporttracking.pedido.ChecklistDocumento;
import com.eversonrubira.exporttracking.pedido.FormaPagamento;
import com.eversonrubira.exporttracking.pedido.Incoterm;
import com.eversonrubira.exporttracking.pedido.Pedido;
import com.eversonrubira.exporttracking.pedido.PedidoEstado;
import com.eversonrubira.exporttracking.pedido.PedidoService;
import com.eversonrubira.exporttracking.pedido.PedidoTransicao;
import com.eversonrubira.exporttracking.pedido.TipoDocumento;
import com.eversonrubira.exporttracking.pedido.exception.PedidoNaoEncontradoException;
import com.eversonrubira.exporttracking.pedido.exception.TransicaoInvalidaException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    }

    private CriarPedidoRequest requestValido() {
        return new CriarPedidoRequest(
                "PO-0001", "Cliente Teste", "Consignee Teste", "China",
                "Porto de Santos", "Porto de Xangai", "Carne bovina",
                new BigDecimal("20.000"), "TON",
                new CondicoesComerciaisRequest(
                        new BigDecimal("85000.00"), "USD", Incoterm.CFR,
                        FormaPagamento.TT_ANTECIPADO, new BigDecimal("30.00")));
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
}
