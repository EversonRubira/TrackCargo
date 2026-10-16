package com.eversonrubira.exporttracking.conta.web;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

// Contexto Spring inteiro + HTTP real + Postgres do docker-compose, como
// o FluxoPedidoE2ETest. Nada mockado de proposito: o que se quer provar
// (cookie de sessao, filtro do Spring Security, BCrypt, indice da base)
// so existe a correr de verdade. Corpos JSON em texto pelo mesmo motivo
// explicado no FluxoPedidoE2ETest (REST Assured nao serializa Jackson 3).
//
// Cada teste usa um email aleatorio, por isso nao depende de limpar a
// base nem da ordem dos testes.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

    private static final String SESSAO = "JSESSIONID";
    private static final String SENHA = "senha-forte-123";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void registoValidoCriaEmpresaEUtilizadorEDevolveSessaoJaAutenticada() {
        String email = novoEmail();

        Response resposta = registar("Exportadora A", "Ana", email, SENHA);

        resposta.then().statusCode(201)
                .body("nome", equalTo("Ana"))
                .body("email", equalTo(email))
                .body("nomeEmpresa", equalTo("Exportadora A"))
                .body("senha", nullValue());

        given().cookie(SESSAO, resposta.getCookie(SESSAO))
                .when().get("/auth/me")
                .then().statusCode(200)
                .body("email", equalTo(email))
                .body("nomeEmpresa", equalTo("Exportadora A"));
    }

    @Test
    void registoComEmailJaUsadoERejeitadoCom409() {
        String email = novoEmail();
        registar("Exportadora A", "Ana", email, SENHA).then().statusCode(201);

        registar("Exportadora B", "Bruno", email, SENHA)
                .then().statusCode(409)
                .body("erro", equalTo("EMAIL_JA_REGISTADO"));
    }

    @Test
    void emailEmMaiusculasETratadoComoOMesmoNoRegistoENoLogin() {
        String email = novoEmail();
        registar("Exportadora A", "Ana", email.toUpperCase(), SENHA)
                .then().statusCode(201)
                .body("email", equalTo(email));

        registar("Exportadora B", "Bruno", email, SENHA)
                .then().statusCode(409)
                .body("erro", equalTo("EMAIL_JA_REGISTADO"));

        login(email.toUpperCase(), SENHA)
                .then().statusCode(200)
                .body("email", equalTo(email));
    }

    @Test
    void loginComCredenciaisCertasCriaSessao() {
        String email = novoEmail();
        registar("Exportadora A", "Ana", email, SENHA).then().statusCode(201);

        Response resposta = login(email, SENHA);

        resposta.then().statusCode(200)
                .body("email", equalTo(email))
                .body("nomeEmpresa", equalTo("Exportadora A"));
        given().cookie(SESSAO, resposta.getCookie(SESSAO))
                .when().get("/auth/me")
                .then().statusCode(200)
                .body("email", equalTo(email));
    }

    // O corpo inteiro da resposta tem de ser igual nos dois casos: se
    // diferisse em qualquer campo (codigo, mensagem, parametros), quem
    // chama saberia se o email existe.
    @Test
    void loginComSenhaErradaOuEmailInexistenteDaAMesmaResposta401() {
        String email = novoEmail();
        registar("Exportadora A", "Ana", email, SENHA).then().statusCode(201);

        Response senhaErrada = login(email, "senha-errada-999");
        Response emailInexistente = login(novoEmail(), SENHA);

        senhaErrada.then().statusCode(401).body("erro", equalTo("CREDENCIAIS_INVALIDAS"));
        emailInexistente.then().statusCode(401).body("erro", equalTo("CREDENCIAIS_INVALIDAS"));
        assertThat(senhaErrada.asString()).isEqualTo(emailInexistente.asString());
        assertThat(senhaErrada.getCookie(SESSAO)).isNull();
    }

    @Test
    void logoutInvalidaASessaoEAMesmaSessaoVoltaAExigirLogin() {
        String sessao = registar("Exportadora A", "Ana", novoEmail(), SENHA).getCookie(SESSAO);
        given().cookie(SESSAO, sessao).when().get("/auth/me").then().statusCode(200);

        given().cookie(SESSAO, sessao).when().post("/auth/logout").then().statusCode(204);

        given().cookie(SESSAO, sessao)
                .when().get("/auth/me")
                .then().statusCode(401)
                .body("erro", equalTo("NAO_AUTENTICADO"));
    }

    @Test
    void meSemSessaoDevolve401NoContratoDeErro() {
        given().when().get("/auth/me")
                .then().statusCode(401)
                .contentType(ContentType.JSON)
                .body("erro", equalTo("NAO_AUTENTICADO"));
    }

    @Test
    void logoutSemSessaoDevolve401() {
        given().when().post("/auth/logout")
                .then().statusCode(401)
                .body("erro", equalTo("NAO_AUTENTICADO"));
    }

    @Test
    void senhaCurtaERejeitadaSemCriarConta() {
        String email = novoEmail();

        registar("Exportadora A", "Ana", email, "123456789")
                .then().statusCode(400)
                .body("erro", equalTo("SENHA_CURTA"))
                .body("parametros.minimo", equalTo(10));

        assertThat(contarUtilizadores(email)).isZero();
    }

    @Test
    void senhaGravadaNaBaseENuncaOTextoPuro() {
        String email = novoEmail();
        registar("Exportadora A", "Ana", email, SENHA).then().statusCode(201);

        String senhaHash = jdbcTemplate.queryForObject(
                "SELECT senha_hash FROM usuario WHERE email = ?", String.class, email);

        assertThat(senhaHash).isNotEqualTo(SENHA).doesNotContain(SENHA).startsWith("{bcrypt}");
    }

    @Test
    void registoComCampoEmBrancoDevolveErroDeValidacao() {
        registar("", "Ana", novoEmail(), SENHA)
                .then().statusCode(400)
                .body("erro", equalTo("VALIDACAO_INVALIDA"))
                .body("campos[0].campo", equalTo("nomeEmpresa"))
                .body("campos[0].codigo", equalTo("OBRIGATORIO"));
    }

    // Prova automatica do que a spec pede confirmar a mao: o navegador
    // so envia o cookie de sessao entre localhost:5173 e :8080 se o
    // preflight responder Allow-Credentials true com a origem exata
    // (nunca "*").
    @Test
    void corsPermiteCookieDeSessaoParaOFrontendLocal() {
        given().header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .when().options("/auth/me")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", "http://localhost:5173")
                .header("Access-Control-Allow-Credentials", "true");
    }

    private Response registar(String nomeEmpresa, String nomeUsuario, String email, String senha) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"nomeEmpresa": "%s", "nomeUsuario": "%s", "email": "%s", "senha": "%s"}
                        """.formatted(nomeEmpresa, nomeUsuario, email, senha))
                .when().post("/auth/registo");
    }

    private Response login(String email, String senha) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "senha": "%s"}
                        """.formatted(email, senha))
                .when().post("/auth/login");
    }

    private Integer contarUtilizadores(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = ?", Integer.class, email);
    }

    private String novoEmail() {
        return "teste-" + UUID.randomUUID() + "@exportadora.com";
    }
}
