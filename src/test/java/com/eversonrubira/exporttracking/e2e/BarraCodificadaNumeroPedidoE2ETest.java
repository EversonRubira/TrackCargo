package com.eversonrubira.exporttracking.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

// Numero de pedido com "/" (o formato automatico NNNNN/AAAA) chega na URL
// como "%2F", e precisa de passar por duas camadas: o Tomcat
// (encodedSolidusHandling no WebConfig) e o firewall do Spring Security
// (HttpFirewall no SecurityConfig). Um @WebMvcTest com MockMvc nao passa
// por nenhuma das duas, por isso este teste sobe o servidor real numa
// porta aleatoria, com a seguranca do projeto ligada, e faz HTTP de
// verdade.
//
// urlEncodingEnabled(false): o REST Assured, por padrao, codificaria de
// novo o "%" (viraria "%252F"). Desligado, a URL sai exatamente como o
// frontend a envia (encodeURIComponent).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BarraCodificadaNumeroPedidoE2ETest {

    @LocalServerPort
    private int port;

    private String numeroPedido;
    private String numeroNaUrl;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        // UUID para nao colidir entre execucoes na mesma base. Fora do
        // padrao NNNNN/AAAA de proposito: assim nao mexe no contador da
        // numeracao automatica. O que importa aqui e a barra.
        numeroPedido = "E2E-" + UUID.randomUUID() + "/2026";
        numeroNaUrl = URLEncoder.encode(numeroPedido, StandardCharsets.UTF_8);
        criarPedido();
    }

    @Test
    void detalheDePedidoComBarraCodificadaChegaAoController() {
        given().urlEncodingEnabled(false)
                .when().get("/pedidos/" + numeroNaUrl)
                .then().statusCode(200)
                .body("numeroPedido", equalTo(numeroPedido));
    }

    @Test
    void rotaAninhadaDoChecklistComBarraCodificadaChegaAoController() {
        given().urlEncodingEnabled(false)
                .when().patch("/pedidos/" + numeroNaUrl + "/documentos/INVOICE/enviar")
                .then().statusCode(204);
    }

    @Test
    void pdfDeStatusComBarraCodificadaChegaAoController() {
        given().urlEncodingEnabled(false)
                .when().get("/pedidos/" + numeroNaUrl + "/status.pdf")
                .then().statusCode(200)
                .contentType("application/pdf");
    }

    private void criarPedido() {
        given().contentType(ContentType.JSON)
                .body("""
                        {
                          "numeroPedido": "%s",
                          "cliente": "Cliente Barra",
                          "consignee": "Consignee Barra",
                          "paisDestino": "China",
                          "portoOrigem": "Porto de Santos",
                          "portoDestino": "Porto de Xangai",
                          "produto": "Soja",
                          "quantidade": 1000,
                          "unidadeMedida": "TON",
                          "condicoesComerciais": {
                            "precoAcordado": 50000,
                            "moeda": "USD",
                            "incoterm": "CFR",
                            "formaPagamento": "TT_ANTECIPADO",
                            "percentualParcial": 30
                          }
                        }
                        """.formatted(numeroPedido))
                .when().post("/pedidos")
                .then().statusCode(201);
    }
}
