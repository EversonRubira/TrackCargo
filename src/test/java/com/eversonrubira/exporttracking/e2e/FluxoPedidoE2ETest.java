package com.eversonrubira.exporttracking.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

// E2E de verdade: sobe o contexto Spring inteiro numa porta aleatoria
// (RANDOM_PORT) e bate via HTTP real contra o Postgres do
// docker-compose - nada de mock aqui, e esse o ponto do E2E. REST
// Assured em vez de Playwright porque hoje nao existe UI nenhuma, so
// API REST; Playwright fica pra quando o frontend (backlog v2)
// existir de verdade - ver docs/SPEC.md e docs/PLAN.md pra decisao.
//
// Corpos de request vao como texto JSON literal (text block), nao
// Map/POJO: REST Assured 5.5.x exige um serializer explicito no
// classpath (Jackson Databind 2.x, Gson, Johnzon ou Yasson) pra
// serializar objeto -> JSON, e o projeto esta em Jackson 3
// (tools.jackson.*), que ele nao reconhece - "Cannot serialize object
// because no JSON serializer found in classpath" ao tentar. Corpo
// String e enviado como esta, sem passar pelo serializer - evita o
// problema de raiz sem precisar adicionar mais uma dependencia so
// pra isso.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FluxoPedidoE2ETest {

    private static final List<String> CHECKLIST_OBRIGATORIO =
            List.of("INVOICE", "PACKING_LIST", "BL", "CERTIFICADO_SANITARIO");

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void fluxoCompletoCriadoAteEntregue() {
        String numeroPedido = novoNumeroPedido();
        criarPedido(numeroPedido);

        for (String tipo : CHECKLIST_OBRIGATORIO) {
            given().pathParam("numero", numeroPedido).pathParam("tipo", tipo)
                    .when().patch("/pedidos/{numero}/documentos/{tipo}/enviar")
                    .then().statusCode(204);
            given().pathParam("numero", numeroPedido).pathParam("tipo", tipo)
                    .when().patch("/pedidos/{numero}/documentos/{tipo}/aceitar")
                    .then().statusCode(204);
        }

        given().pathParam("numero", numeroPedido)
                .when().get("/pedidos/{numero}")
                .then().statusCode(200).body("estado", equalTo("DOCUMENTACAO_ACEITA"));

        given().pathParam("numero", numeroPedido)
                .when().post("/pedidos/{numero}/pagamento-parcial")
                .then().statusCode(200).body("estado", equalTo("PAGAMENTO_PARCIAL_RECEBIDO"));

        transicionar(numeroPedido, "EMBARCADO");

        // Cia maritima/container so ficam conhecidos depois que o navio
        // sai - sem regra de transicao de estado, so precisa poder ser
        // preenchido quando a informacao chegar (ver SPEC.md).
        given().pathParam("numero", numeroPedido).contentType(ContentType.JSON)
                .body("""
                        { "ciaMaritima": "Maersk", "numeroContainer": "MSKU1234567" }
                        """)
                .when().patch("/pedidos/{numero}/logistica")
                .then().statusCode(200)
                .body("ciaMaritima", equalTo("Maersk"))
                .body("numeroContainer", equalTo("MSKU1234567"));

        given().queryParam("estado", "EMBARCADO")
                .when().get("/pedidos")
                .then().statusCode(200)
                .body("find { it.numeroPedido == '%s' }.ciaMaritima".formatted(numeroPedido), equalTo("Maersk"));

        given().pathParam("numero", numeroPedido)
                .when().post("/pedidos/{numero}/pagamento-saldo")
                .then().statusCode(200).body("estado", equalTo("PAGAMENTO_SALDO_RECEBIDO"));

        transicionar(numeroPedido, "DOCUMENTOS_ORIGINAIS_ENVIADOS");
        transicionar(numeroPedido, "ENTREGUE");

        Response historico = given().pathParam("numero", numeroPedido)
                .when().get("/pedidos/{numero}/historico")
                .then().statusCode(200)
                .extract().response();

        assertThat(historico.jsonPath().getList("estadoNovo", String.class)).containsExactly(
                "CRIADO",
                "DOCUMENTACAO_ENVIADA",
                "DOCUMENTACAO_ACEITA",
                "PAGAMENTO_PARCIAL_RECEBIDO",
                "EMBARCADO",
                "PAGAMENTO_SALDO_RECEBIDO",
                "DOCUMENTOS_ORIGINAIS_ENVIADOS",
                "ENTREGUE");
        assertThat(historico.jsonPath().getList("estadoAnterior", String.class).get(0)).isNull();

        given().pathParam("numero", numeroPedido)
                .when().get("/pedidos/{numero}/status.pdf")
                .then().statusCode(200)
                .contentType("application/pdf");
    }

    @Test
    void listarSemFiltroIncluiPedidoRecemCriadoEComFiltroDeEstadoSoOsQueBatem() {
        String numeroPedido = novoNumeroPedido();
        criarPedido(numeroPedido);

        given().when().get("/pedidos")
                .then().statusCode(200)
                .body("numeroPedido", hasItem(numeroPedido));

        given().queryParam("estado", "CRIADO")
                .when().get("/pedidos")
                .then().statusCode(200)
                .body("numeroPedido", hasItem(numeroPedido));

        given().queryParam("estado", "ENTREGUE")
                .when().get("/pedidos")
                .then().statusCode(200)
                .body("numeroPedido", not(hasItem(numeroPedido)));
    }

    @Test
    void cancelamentoAntesDoEmbarqueImpedeQualquerTransicaoDepois() {
        String numeroPedido = novoNumeroPedido();
        criarPedido(numeroPedido);

        given().pathParam("numero", numeroPedido).contentType(ContentType.JSON)
                .body(corpoTransicionar("CANCELADO"))
                .when().patch("/pedidos/{numero}/transicionar")
                .then().statusCode(200).body("estado", equalTo("CANCELADO"));

        given().pathParam("numero", numeroPedido).contentType(ContentType.JSON)
                .body(corpoTransicionar("EMBARCADO"))
                .when().patch("/pedidos/{numero}/transicionar")
                .then().statusCode(409)
                .body("erro", equalTo("TRANSICAO_INVALIDA"))
                .body("estadoAtual", equalTo("CANCELADO"))
                .body("estadoSolicitado", equalTo("EMBARCADO"));
    }

    @Test
    void recusarDocumentoEReenviarAtualizaDataDeEnvio() throws InterruptedException {
        String numeroPedido = novoNumeroPedido();
        criarPedido(numeroPedido);

        given().pathParam("numero", numeroPedido).pathParam("tipo", "INVOICE")
                .when().patch("/pedidos/{numero}/documentos/{tipo}/enviar")
                .then().statusCode(204);

        String primeiroEnvio = enviadoEmDoInvoice(numeroPedido);
        assertThat(primeiroEnvio).isNotNull();

        given().pathParam("numero", numeroPedido).pathParam("tipo", "INVOICE").contentType(ContentType.JSON)
                .body(corpoRecusar("Invoice com valor divergente do contrato"))
                .when().patch("/pedidos/{numero}/documentos/{tipo}/recusar")
                .then().statusCode(204);

        assertThat(enviadoEmDoInvoice(numeroPedido)).isNull();

        Thread.sleep(5);

        given().pathParam("numero", numeroPedido).pathParam("tipo", "INVOICE")
                .when().patch("/pedidos/{numero}/documentos/{tipo}/enviar")
                .then().statusCode(204);

        String segundoEnvio = enviadoEmDoInvoice(numeroPedido);
        assertThat(segundoEnvio).isNotNull();
        assertThat(segundoEnvio).isNotEqualTo(primeiroEnvio);
    }

    @Test
    void recusarDocumentoJaAceitoRetorna409NaAPIReal() {
        String numeroPedido = novoNumeroPedido();
        criarPedido(numeroPedido);

        given().pathParam("numero", numeroPedido).pathParam("tipo", "INVOICE")
                .when().patch("/pedidos/{numero}/documentos/{tipo}/enviar")
                .then().statusCode(204);
        given().pathParam("numero", numeroPedido).pathParam("tipo", "INVOICE")
                .when().patch("/pedidos/{numero}/documentos/{tipo}/aceitar")
                .then().statusCode(204);

        given().pathParam("numero", numeroPedido).pathParam("tipo", "INVOICE").contentType(ContentType.JSON)
                .body(corpoRecusar("Tentativa invalida"))
                .when().patch("/pedidos/{numero}/documentos/{tipo}/recusar")
                .then().statusCode(409)
                .body("erro", equalTo("DOCUMENTO_JA_ACEITO"));
    }

    @Test
    void statusPdfMostraMotivoDaRecusaEDepoisOStatusEnviadoComARecusaAindaListada() throws Exception {
        String numeroPedido = novoNumeroPedido();
        criarPedido(numeroPedido);

        given().pathParam("numero", numeroPedido).pathParam("tipo", "INVOICE")
                .when().patch("/pedidos/{numero}/documentos/{tipo}/enviar")
                .then().statusCode(204);

        String motivo = "Invoice com valor divergente do contrato assinado";
        given().pathParam("numero", numeroPedido).pathParam("tipo", "INVOICE").contentType(ContentType.JSON)
                .body(corpoRecusar(motivo))
                .when().patch("/pedidos/{numero}/documentos/{tipo}/recusar")
                .then().statusCode(204);

        byte[] pdfAposRecusa = statusPdf(numeroPedido);
        String textoAposRecusa = extrairTexto(pdfAposRecusa);
        assertThat(textoAposRecusa).contains(motivo);
        assertThat(textoAposRecusa).contains("Recusado, aguardando reenvio");

        given().pathParam("numero", numeroPedido).pathParam("tipo", "INVOICE")
                .when().patch("/pedidos/{numero}/documentos/{tipo}/enviar")
                .then().statusCode(204);

        byte[] pdfAposReenvio = statusPdf(numeroPedido);
        String textoAposReenvio = extrairTexto(pdfAposReenvio);
        // Documento voltou a "Enviado" (nao mais "aguardando reenvio"),
        // mas a recusa antiga continua no historico do PDF - reenvio
        // nunca apaga pedido_ocorrencia.
        assertThat(textoAposReenvio).contains(motivo);
        assertThat(textoAposReenvio.replaceAll("\\s+", " ")).contains("Invoice Enviado");
    }

    @Test
    void pagamentoSaldoForaDeSequenciaRetorna409NaAPIReal() {
        String numeroPedido = novoNumeroPedido();
        criarPedido(numeroPedido);

        given().pathParam("numero", numeroPedido)
                .when().post("/pedidos/{numero}/pagamento-saldo")
                .then().statusCode(409)
                .body("erro", equalTo("TRANSICAO_INVALIDA"))
                .body("estadoAtual", equalTo("CRIADO"))
                .body("estadoSolicitado", equalTo("PAGAMENTO_SALDO_RECEBIDO"));
    }

    private void transicionar(String numeroPedido, String novoEstado) {
        given().pathParam("numero", numeroPedido).contentType(ContentType.JSON)
                .body(corpoTransicionar(novoEstado))
                .when().patch("/pedidos/{numero}/transicionar")
                .then().statusCode(200).body("estado", equalTo(novoEstado));
    }

    private void criarPedido(String numeroPedido) {
        given().contentType(ContentType.JSON)
                .body(corpoCriacao(numeroPedido))
                .when().post("/pedidos")
                .then().statusCode(201)
                .body("numeroPedido", equalTo(numeroPedido))
                .body("estado", equalTo("CRIADO"))
                .body("checklist.size()", equalTo(4));
    }

    private String corpoTransicionar(String novoEstado) {
        return """
                { "novoEstado": "%s" }
                """.formatted(novoEstado);
    }

    private String corpoRecusar(String motivo) {
        return """
                { "motivo": "%s" }
                """.formatted(motivo);
    }

    private String enviadoEmDoInvoice(String numeroPedido) {
        return given().pathParam("numero", numeroPedido)
                .when().get("/pedidos/{numero}")
                .then().statusCode(200)
                .extract().response()
                .jsonPath().getString("checklist.find { it.tipoDocumento == 'INVOICE' }.enviadoEm");
    }

    private String corpoCriacao(String numeroPedido) {
        return """
                {
                  "numeroPedido": "%s",
                  "cliente": "Cliente E2E",
                  "consignee": "Consignee E2E",
                  "paisDestino": "China",
                  "portoOrigem": "Porto de Santos",
                  "portoDestino": "Porto de Xangai",
                  "produto": "Carne bovina",
                  "quantidade": 20.000,
                  "unidadeMedida": "TON",
                  "condicoesComerciais": {
                    "precoAcordado": 85000.00,
                    "moeda": "USD",
                    "incoterm": "CFR",
                    "formaPagamento": "TT_ANTECIPADO",
                    "percentualParcial": 30.00
                  }
                }
                """.formatted(numeroPedido);
    }

    private String novoNumeroPedido() {
        return "E2E-" + UUID.randomUUID();
    }

    private byte[] statusPdf(String numeroPedido) {
        return given().pathParam("numero", numeroPedido)
                .when().get("/pedidos/{numero}/status.pdf")
                .then().statusCode(200)
                .contentType("application/pdf")
                .extract().asByteArray();
    }

    private String extrairTexto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder texto = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina)).append(' ');
            }
            return texto.toString();
        } finally {
            reader.close();
        }
    }
}
