package com.eversonrubira.exporttracking.triage.provider;

import com.eversonrubira.exporttracking.triage.TestSupport;
import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import com.eversonrubira.exporttracking.triage.context.Artifact;
import com.eversonrubira.exporttracking.triage.context.ArtifactKind;
import com.eversonrubira.exporttracking.triage.context.ContextPackage;
import com.eversonrubira.exporttracking.triage.security.SanitizedContext;
import com.eversonrubira.exporttracking.triage.security.SecretFilter;
import com.eversonrubira.exporttracking.triage.validation.Confidence;
import com.eversonrubira.exporttracking.triage.validation.SampleDiagnosis;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T07 + T08: comportamento do ADR-003 contra um servidor local. Nunca chama o Claude real.
class ClaudeClientTest {

    // Valores montados em tempo de execucao: o codigo-fonte nao contem nada que pareca segredo real.
    static final String API_KEY = "sk-" + "ant-api03-" + "TestKeyTestKeyTestKey1234";
    static final String DB_PASSWORD = "p4ss-" + "w0rd-" + "X7";
    static final String ENV_SECRET = "env-" + "value-" + "9f8e7d6c";
    static final String GITHUB_TOKEN = "ghp" + "_" + "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8";
    static final List<String> ALL_SECRETS = List.of(API_KEY, DB_PASSWORD, ENV_SECRET, GITHUB_TOKEN);

    static final String VALID = SampleDiagnosis.json(SampleDiagnosis.valid());

    private final SecretFilter filter = new SecretFilter(Map.of(
            "ANTHROPIC_API_KEY", API_KEY,
            "SOME_SERVICE_PASSWORD", ENV_SECRET));

    private SanitizedContext context() {
        return filter.sanitize(contextWithSecrets());
    }

    private static ContextPackage contextWithSecrets() {
        FailureInput failure = new FailureInput("com.acme.shop.OrderServiceTest", "shipsOrder", "java.lang.AssertionError",
                "expected SHIPPED but was CREATED; client sent " + API_KEY,
                "java.lang.AssertionError: boom\n\tat com.acme.shop.OrderService.ship(OrderService.java:14)\nAuthorization: Bearer " + GITHUB_TOKEN,
                "spring.datasource.password=" + DB_PASSWORD + "\nconnected with " + ENV_SECRET,
                List.of());
        Artifact artifact = new Artifact(ArtifactKind.DOCUMENT, "docs/SPEC.md", "doc", "token=" + GITHUB_TOKEN + " and key " + API_KEY, false);
        return new ContextPackage(failure, List.of(artifact), List.of("saw " + ENV_SECRET));
    }

    private static TriageConfig config(FakeProvider provider, Map<String, String> extra) {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("triage.provider.base-url", provider.baseUrl());
        overrides.put("triage.provider.retry-delay-millis", "0");
        overrides.putAll(extra);
        return TestSupport.sampleConfig(overrides);
    }

    private static ClaudeClient client(FakeProvider provider, List<String> log) {
        return new ClaudeClient(config(provider, Map.of()), API_KEY, log::add);
    }

    // ---------- sucesso ----------

    @Test
    void successReturnsAValidatedDiagnosisWithTokenCountsAfterOneRequest() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
            ProviderOutcome outcome = client(provider, new ArrayList<>()).diagnose(context());

            assertTrue(outcome.succeeded(), outcome.detail());
            assertEquals(Confidence.MEDIUM, outcome.diagnosis().confidence());
            assertNull(outcome.reason());
            assertEquals(1, outcome.attempts());
            assertEquals(1234, outcome.inputTokens());
            assertEquals(321, outcome.outputTokens());
            assertEquals(1, provider.hits());

            FakeProvider.Seen request = provider.seen().get(0);
            assertEquals("POST", request.method());
            assertEquals("/v1/messages", request.path());
            assertEquals(API_KEY, request.headers().get("x-api-key"));
            assertEquals("2023-06-01", request.headers().get("anthropic-version"));
            assertEquals("application/json", request.headers().get("content-type"));
            assertTrue(request.body().contains("\"model\":\"claude-sonnet-5-5\""), request.body());
            assertFalse(request.body().contains("\"tools\""));
            assertFalse(request.body().contains("tool_choice"));
        }
    }

    @Test
    void textBlocksAreJoinedAndAFencedObjectIsAccepted() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.ok("```json\n" + VALID + "\n```"))) {
            assertTrue(client(provider, new ArrayList<>()).diagnose(context()).succeeded());
        }
    }

    // ---------- T07 DoD: o que sai ja passou pelo Secret Filter ----------

    @Test
    void thePayloadSentToTheProviderHasNoCredentialAndKeepsTheMask() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
            client(provider, new ArrayList<>()).diagnose(context());

            String body = provider.seen().get(0).body();
            for (String secret : ALL_SECRETS) {
                assertFalse(body.contains(secret), "credential reached the provider input");
            }
            assertTrue(body.contains(SecretFilter.MASK), "the mask should be visible in what was sent");
            // o contexto util continua la: so os segredos saem
            assertTrue(body.contains("OrderService.java:14"));
            assertTrue(body.contains("expected SHIPPED but was CREATED"));
        }
    }

    // O tipo e a garantia: nao existe maneira de passar um ContextPackage sem filtrar ao cliente.
    @Test
    void noPublicMethodOfTheClientAcceptsAnUnfilteredContext() {
        for (Method method : ClaudeClient.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter == ContextPackage.class || parameter == FailureInput.class || parameter == String.class,
                        "public method " + method.getName() + " accepts unfiltered data: " + parameter.getSimpleName());
            }
        }
    }

    @Test
    void credentialsNeverAppearInLogsOrOutcomesEvenWhenTheServerEchoesThem() throws Exception {
        String echo = "{\"error\":\"bad request from " + API_KEY + " " + DB_PASSWORD + " " + ENV_SECRET + " " + GITHUB_TOKEN + "\"}";
        String echoedAsAnswer = "I saw " + API_KEY + " and " + DB_PASSWORD + " in the log " + ENV_SECRET + " " + GITHUB_TOKEN;
        List<List<FakeProvider.Reply>> scenarios = List.of(
                List.of(FakeProvider.status(500, echo)),                         // erro transitorio com eco
                List.of(FakeProvider.status(401, echo)),                         // credencial invalida com eco
                List.of(FakeProvider.status(400, echo)),                         // pedido rejeitado com eco
                List.of(FakeProvider.ok(echoedAsAnswer)),                        // resposta invalida que repete segredos
                List.of(FakeProvider.status(200, "<html>" + echo + "</html>")),  // envelope que nao e JSON
                List.of(FakeProvider.status(503, echo), FakeProvider.ok(VALID)));
        for (List<FakeProvider.Reply> scenario : scenarios) {
            try (FakeProvider provider = new FakeProvider(scenario.toArray(new FakeProvider.Reply[0]))) {
                List<String> log = new ArrayList<>();
                ProviderOutcome outcome = client(provider, log).diagnose(context());

                assertFalse(log.isEmpty(), "the client should log what happened");
                String everything = String.join("\n", log) + "\n" + outcome;
                for (String secret : ALL_SECRETS) {
                    assertFalse(everything.contains(secret), "credential leaked in log/outcome: " + everything);
                }
            }
        }
    }

    // ---------- timeout, retry, limites (ADR-003) ----------

    @Test
    void timeoutIsRetriedOnceAndThenReported() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.slow(3000, VALID))) {
            ClaudeClient client = new ClaudeClient(config(provider, Map.of("triage.provider.timeout-seconds", "1")), API_KEY, new ArrayList<String>()::add);
            ProviderOutcome outcome = client.diagnose(context());

            assertFalse(outcome.succeeded());
            assertEquals(FailureReason.TIMEOUT, outcome.reason());
            assertEquals(2, outcome.attempts());
            assertEquals(2, provider.hits());
            assertTrue(outcome.elapsedMillis() >= 1900, "two timed-out attempts should take about 2 s, took " + outcome.elapsedMillis());
            assertTrue(outcome.elapsedMillis() < 5000, "the client waited for the slow answer: " + outcome.elapsedMillis());
        }
    }

    @Test
    void aTransientErrorIsRetriedAndASecondAttemptCanSucceed() throws Exception {
        for (int status : new int[]{429, 500, 502, 503, 529}) {
            try (FakeProvider provider = new FakeProvider(FakeProvider.status(status, "{}"), FakeProvider.ok(VALID))) {
                ProviderOutcome outcome = client(provider, new ArrayList<>()).diagnose(context());
                assertTrue(outcome.succeeded(), "HTTP " + status + ": " + outcome.detail());
                assertEquals(2, outcome.attempts());
                assertEquals(2, provider.hits());
            }
        }
    }

    @Test
    void aTransientErrorThatPersistsStopsAtTwoAttempts() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.status(503, "{}"))) {
            ProviderOutcome outcome = client(provider, new ArrayList<>()).diagnose(context());
            assertEquals(FailureReason.PROVIDER_ERROR, outcome.reason());
            assertEquals("HTTP 503", outcome.detail());
            assertEquals(2, outcome.attempts());
            assertEquals(2, provider.hits());
        }
    }

    @Test
    void theAttemptCeilingFromTheConfigIsHonored() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.status(503, "{}"), FakeProvider.ok(VALID))) {
            ClaudeClient client = new ClaudeClient(config(provider, Map.of("triage.provider.max-attempts", "1")), API_KEY, new ArrayList<String>()::add);
            ProviderOutcome outcome = client.diagnose(context());
            assertFalse(outcome.succeeded());
            assertEquals(1, outcome.attempts());
            assertEquals(1, provider.hits());
        }
    }

    @Test
    void thePauseBetweenAttemptsIsApplied() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.status(503, "{}"), FakeProvider.ok(VALID))) {
            ClaudeClient client = new ClaudeClient(config(provider, Map.of("triage.provider.retry-delay-millis", "400")), API_KEY, new ArrayList<String>()::add);
            ProviderOutcome outcome = client.diagnose(context());
            assertTrue(outcome.succeeded());
            assertTrue(outcome.elapsedMillis() >= 380, "no pause before the retry: " + outcome.elapsedMillis());
        }
    }

    @Test
    void anInvalidCredentialIsNotRetried() throws Exception {
        for (int status : new int[]{401, 403}) {
            try (FakeProvider provider = new FakeProvider(FakeProvider.status(status, "{}"), FakeProvider.ok(VALID))) {
                ProviderOutcome outcome = client(provider, new ArrayList<>()).diagnose(context());
                assertEquals(FailureReason.INVALID_CREDENTIALS, outcome.reason());
                assertEquals(1, outcome.attempts());
                assertEquals(1, provider.hits());
            }
        }
    }

    @Test
    void otherClientErrorsAreNotRetried() throws Exception {
        for (int status : new int[]{400, 404, 413, 422}) {
            try (FakeProvider provider = new FakeProvider(FakeProvider.status(status, "{}"), FakeProvider.ok(VALID))) {
                ProviderOutcome outcome = client(provider, new ArrayList<>()).diagnose(context());
                assertEquals(FailureReason.REQUEST_REJECTED, outcome.reason(), "HTTP " + status);
                assertEquals(1, provider.hits());
            }
        }
    }

    @Test
    void anUnavailableProviderIsRetriedAndReportedWithoutThrowing() throws Exception {
        String deadUrl;
        try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
            deadUrl = provider.baseUrl();
        }   // fechado: ninguem escuta nesta porta
        TriageConfig config = TestSupport.sampleConfig(Map.of(
                "triage.provider.base-url", deadUrl, "triage.provider.retry-delay-millis", "0"));
        List<String> log = new ArrayList<>();
        ProviderOutcome outcome = new ClaudeClient(config, API_KEY, log::add).diagnose(context());

        assertFalse(outcome.succeeded());
        assertEquals(FailureReason.PROVIDER_ERROR, outcome.reason());
        assertEquals(2, outcome.attempts());
        assertTrue(log.stream().anyMatch(line -> line.contains("retrying")));
    }

    @Test
    void withoutAnApiKeyNothingIsSent() throws Exception {
        for (String key : new String[]{null, "", "   "}) {
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                List<String> log = new ArrayList<>();
                ProviderOutcome outcome = new ClaudeClient(config(provider, Map.of()), key, log::add).diagnose(context());
                assertEquals(FailureReason.MISSING_API_KEY, outcome.reason());
                assertEquals(0, outcome.attempts());
                assertEquals(0, provider.hits());
                assertTrue(outcome.detail().contains("ANTHROPIC_API_KEY"), "should name the variable to set");
            }
        }
    }

    // ---------- T08 integrado na politica de retry ----------

    @Test
    void anInvalidResponseIsRoutedThroughTheRetryPolicy() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.ok("Sure, here is a diagnosis in prose."), FakeProvider.ok(VALID))) {
            ProviderOutcome outcome = client(provider, new ArrayList<>()).diagnose(context());
            assertTrue(outcome.succeeded());
            assertEquals(2, outcome.attempts());
        }
    }

    @Test
    void twoInvalidResponsesEndAsInvalidResponseNamingTheBrokenFields() throws Exception {
        Map<String, Object> noEvidence = SampleDiagnosis.valid();
        noEvidence.remove("evidence");
        noEvidence.put("confidence", "CERTAIN");
        try (FakeProvider provider = new FakeProvider(FakeProvider.ok(SampleDiagnosis.json(noEvidence)))) {
            ProviderOutcome outcome = client(provider, new ArrayList<>()).diagnose(context());
            assertNull(outcome.diagnosis());
            assertEquals(FailureReason.INVALID_RESPONSE, outcome.reason());
            assertEquals(2, outcome.attempts());
            assertEquals(2, provider.hits());
            assertTrue(outcome.detail().contains("evidence"), outcome.detail());
            assertTrue(outcome.detail().contains("confidence"), outcome.detail());
        }
    }

    @Test
    void anEmptyAnswerAndAnEnvelopeWithoutTextAreInvalid() throws Exception {
        for (FakeProvider.Reply reply : List.of(
                FakeProvider.ok(""),
                FakeProvider.status(200, "{\"content\":[],\"stop_reason\":\"end_turn\"}"),
                FakeProvider.status(200, "{\"content\":[{\"type\":\"tool_use\",\"id\":\"x\"}]}"),
                FakeProvider.status(200, "not json"),
                FakeProvider.status(200, "[]"))) {
            try (FakeProvider provider = new FakeProvider(reply)) {
                ProviderOutcome outcome = client(provider, new ArrayList<>()).diagnose(context());
                assertEquals(FailureReason.INVALID_RESPONSE, outcome.reason(), reply.body());
                assertEquals(2, provider.hits());
            }
        }
    }

    @Test
    void aResponseCutByTheTokenLimitIsInvalidEvenIfItParses() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.okCut(VALID))) {
            ProviderOutcome outcome = client(provider, new ArrayList<>()).diagnose(context());
            assertEquals(FailureReason.INVALID_RESPONSE, outcome.reason());
            assertTrue(outcome.detail().contains("token limit"));
        }
    }

    // ---------- configuracao do pedido ----------

    @Test
    void theRequestCarriesTheConfiguredTimeoutAndEndpoint() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
            HttpRequest request = new ClaudeClient(config(provider, Map.of()), API_KEY, s -> { }).buildRequest("{}");
            assertEquals(Duration.ofSeconds(30), request.timeout().orElseThrow());       // ADR-003
            assertEquals(provider.baseUrl() + "/v1/messages", request.uri().toString());
            assertEquals("POST", request.method());

            HttpRequest custom = new ClaudeClient(config(provider, Map.of("triage.provider.timeout-seconds", "7")), API_KEY, s -> { }).buildRequest("{}");
            assertEquals(Duration.ofSeconds(7), custom.timeout().orElseThrow());

            HttpRequest slash = new ClaudeClient(config(provider, Map.of("triage.provider.base-url", provider.baseUrl() + "/")), API_KEY, s -> { }).buildRequest("{}");
            assertEquals(provider.baseUrl() + "/v1/messages", slash.uri().toString());
        }
    }

    @Test
    void theModelAndTokenLimitComeFromTheConfig() throws Exception {
        try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
            ClaudeClient client = new ClaudeClient(config(provider, Map.of(
                    "triage.provider.model", "claude-test-model", "triage.provider.max-output-tokens", "512")), API_KEY, s -> { });
            client.diagnose(context());
            String body = provider.seen().get(0).body();
            assertTrue(body.contains("\"model\":\"claude-test-model\""), body);
            assertTrue(body.contains("\"max_tokens\":512"), body);
            assertNotNull(provider.seen().get(0).headers().get("x-api-key"));
        }
    }
}
