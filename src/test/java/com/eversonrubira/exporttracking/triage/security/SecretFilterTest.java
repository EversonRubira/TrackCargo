package com.eversonrubira.exporttracking.triage.security;

import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.context.Artifact;
import com.eversonrubira.exporttracking.triage.context.ArtifactKind;
import com.eversonrubira.exporttracking.triage.context.ContextPackage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T05: nenhum segredo conhecido sai do processo sem mascara.
// Os valores sao montados em tempo de execucao para o codigo-fonte nao conter
// nada que pareca um segredo real (e nao disparar scanners de segredos).
class SecretFilterTest {

    static final String ENV_SECRET = "env-" + "value-" + "9f8e7d6c";
    static final String ANTHROPIC_KEY = "sk-" + "ant-api03-" + "AbCdEfGhIjKlMnOpQrSt";
    static final String GITHUB_TOKEN = "ghp" + "_" + "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8";
    static final String AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";
    static final String BEARER = "abcdef" + "0123456789" + "xyz";
    static final String JWT = "eyJ" + "hbGciOiJIUzI1NiJ9." + "eyJzdWIiOiIxMjM0NTY3ODkwIn0." + "dBjftJeZ4CVPmB92K27uhbUJU1p1r";
    static final String DB_PASSWORD = "p4ss-" + "w0rd-" + "X7";

    private final SecretFilter filter = new SecretFilter(Map.of(
            "ANTHROPIC_API_KEY", ENV_SECRET,
            "PATH", "/usr/local/bin:/usr/bin",       // nome sem ar de segredo: nao entra
            "SHORT_TOKEN", "abc"));                    // valor curto demais: nao entra

    private void assertMasked(String input, String secret) {
        String out = filter.mask(input);
        assertFalse(out.contains(secret), "secret leaked: " + out);
        assertTrue(out.contains(SecretFilter.MASK), "nothing was masked: " + out);
        assertEquals(out, filter.mask(out), "masking must be idempotent");
    }

    @Test
    void vendorTokensAreMasked() {
        assertMasked("key is " + ANTHROPIC_KEY + " in header", ANTHROPIC_KEY);
        assertMasked("token " + GITHUB_TOKEN + " leaked", GITHUB_TOKEN);
        assertMasked("aws " + AWS_KEY, AWS_KEY);
        assertMasked("jwt " + JWT, JWT);
    }

    @Test
    void bearerAndBasicCredentialsAreMaskedButTheSchemeStays() {
        assertMasked("Authorization: Bearer " + BEARER, BEARER);
        assertTrue(filter.mask("Authorization: Bearer " + BEARER).contains("Bearer " + SecretFilter.MASK));
        assertMasked("Authorization: Basic " + BEARER, BEARER);
    }

    @Test
    void secretLikeAssignmentsAreMaskedInEveryCommonFormat() {
        assertMasked("spring.datasource.password=" + DB_PASSWORD, DB_PASSWORD);
        assertMasked("DB_PASSWORD: " + DB_PASSWORD, DB_PASSWORD);
        assertMasked("{\"apiKey\": \"" + DB_PASSWORD + "\"}", DB_PASSWORD);
        assertMasked("client_secret='" + DB_PASSWORD + "'", DB_PASSWORD);
        assertMasked("senha=" + DB_PASSWORD, DB_PASSWORD);
        assertMasked("X-Api-Key: " + DB_PASSWORD, DB_PASSWORD);
    }

    @Test
    void credentialsInUrlsAndPemBlocksAreMasked() {
        assertMasked("jdbc:postgresql://app:" + DB_PASSWORD + "@db:5432/x", DB_PASSWORD);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + "MIIEvQIBADANBgkqhkiG9w0B" + "\n-----END PRIVATE KEY-----";
        assertMasked("before\n" + pem + "\nafter", "MIIEvQIBADANBgkqhkiG9w0B");
    }

    @Test
    void knownEnvironmentValuesAreMaskedEvenWithoutAnyPattern() {
        assertMasked("the value was " + ENV_SECRET + " today", ENV_SECRET);
    }

    @Test
    void ordinaryTextIsLeftAlone() {
        List<String> harmless = List.of(
                "com.eversonrubira.exporttracking.conta.exception.SenhaCurtaException: Senha deve ter pelo menos 10 bytes",
                "pedido 01001/2026 em estado EMBARCADO",
                "expected: <SHIPPED> but was: <CREATED>",
                "at com.acme.shop.OrderService.ship(OrderService.java:14)",
                "PATH=/usr/local/bin:/usr/bin",
                "SHORT_TOKEN is abc",
                "the token was refreshed");
        for (String text : harmless) {
            assertEquals(text, filter.mask(text));
        }
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertEquals("", filter.mask((String) null));
        assertEquals("", filter.mask(""));
    }

    @Test
    void wholePackageIsMaskedInEveryField() {
        FailureInput failure = new FailureInput("c.CTest", "t", "x.Y",
                "bad key " + ANTHROPIC_KEY,
                "x.Y: boom\n\tat c.C.m(C.java:1)\nAuthorization: Bearer " + BEARER,
                "spring.datasource.password=" + DB_PASSWORD, List.of());
        Artifact artifact = new Artifact(ArtifactKind.DOCUMENT, "docs/x.md", "doc", "token=" + GITHUB_TOKEN, false);
        ContextPackage pkg = new ContextPackage(failure, List.of(artifact), List.of("saw " + ENV_SECRET));

        ContextPackage masked = filter.mask(pkg);
        String text = masked.toInspectableText();
        for (String secret : List.of(ANTHROPIC_KEY, BEARER, DB_PASSWORD, GITHUB_TOKEN, ENV_SECRET)) {
            assertFalse(text.contains(secret), "leaked: " + secret);
        }
        // O que nao e segredo mantem-se, para o diagnostico continuar util.
        assertTrue(text.contains("at c.C.m(C.java:1)"));
        assertEquals(1, masked.artifacts().size());
        assertEquals(masked, filter.mask(masked));
    }
}
