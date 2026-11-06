package com.eversonrubira.exporttracking.triage;

import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import com.eversonrubira.exporttracking.triage.context.ContextBuilder;
import com.eversonrubira.exporttracking.triage.context.ContextPackage;
import com.eversonrubira.exporttracking.triage.context.DeterministicRetriever;
import com.eversonrubira.exporttracking.triage.context.DocumentMap;
import com.eversonrubira.exporttracking.triage.rendering.SafeText;
import com.eversonrubira.exporttracking.triage.security.SecretFilter;
import com.eversonrubira.exporttracking.triage.provider.FakeProvider;
import com.eversonrubira.exporttracking.triage.validation.SampleDiagnosis;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T10/T11/T12: o pipeline completo contra um servidor local. Nenhum teste automatico chama o Claude real.
class TriagePipelineTest {

    static final String API_KEY = "sk-" + "ant-api03-" + "TestKeyTestKeyTestKey1234";
    static final String ENV_SECRET = "env-" + "value-" + "9f8e7d6c";
    static final String DB_PASSWORD = "p4ss-" + "w0rd-" + "X7";
    static final String GITHUB_TOKEN = "ghp" + "_" + "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8";
    static final String BEARER = "abcdef" + "0123456789" + "xyz";
    static final List<String> ALL_SECRETS = List.of(API_KEY, ENV_SECRET, DB_PASSWORD, GITHUB_TOKEN, BEARER);

    static final String VALID = SampleDiagnosis.json(SampleDiagnosis.valid());
    static final String STACK = "com.acme.shop.InvalidTransitionException: boom\n"
            + "\tat com.acme.shop.OrderService.ship(OrderService.java:14)\n"
            + "\tat com.acme.shop.OrderServiceTest.%s(OrderServiceTest.java:22)\n";

    record Run(int exit, String output) {
    }

    /** Escreve um TEST-*.xml com um teste falhado por nome. */
    static void writeReport(Path dir, String fileClass, List<String> methods, String message, String log) throws Exception {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"" + fileClass + "\">\n");
        for (String method : methods) {
            xml.append("  <testcase name=\"").append(method).append("\" classname=\"").append(fileClass).append("\">\n")
                    .append("    <failure message=\"").append(message).append("\" type=\"com.acme.shop.InvalidTransitionException\"><![CDATA[")
                    .append(String.format(STACK, method)).append("]]></failure>\n")
                    .append("    <system-out><![CDATA[").append(log).append("]]></system-out>\n  </testcase>\n");
        }
        xml.append("</testsuite>\n");
        Files.writeString(dir.resolve("TEST-" + fileClass + ".xml"), xml.toString());
    }

    static List<String> methods(int count) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            names.add("test" + i);
        }
        return names;
    }

    static Run run(Path reports, FakeProvider provider, Map<String, String> env, Map<String, String> overrides) {
        Map<String, String> all = new HashMap<>();
        all.put("triage.reports-dir", reports.toString());
        all.put("triage.provider.base-url", provider.baseUrl());
        all.put("triage.provider.retry-delay-millis", "0");
        all.putAll(overrides);
        TriageConfig config = TestSupport.sampleConfig(all);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exit = TriageApplication.run(config, env, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return new Run(exit, buffer.toString(StandardCharsets.UTF_8));
    }

    private static int count(String text, String needle) {
        Matcher m = Pattern.compile(Pattern.quote(needle)).matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static String metricsFor(String output, String test) {
        return output.lines().filter(l -> l.startsWith("| [triage-metrics] status=") && l.endsWith("test=" + test)).findFirst().orElse("");
    }

    // ---------- o caminho pedido: falha -> triage -> MISSING_API_KEY -> TRIAGE FAILED -> falha original intacta ----------

    @Test
    void withoutAnApiKeyTheTriageFailsGracefullyAndSaysSo() throws Exception {
        TestSupport.withTempDir(reports -> {
            writeReport(reports, "com.acme.shop.OrderServiceTest", methods(1), "Cannot ship", "log line");
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of(), Map.of());

                assertEquals(0, run.exit(), "the triage must never fail the pipeline");
                assertEquals(0, provider.hits(), "no request without credentials");
                String out = run.output();
                assertTrue(out.contains("| TRIAGE FAILED\n"), out);
                assertTrue(out.contains("| Reason: No API key is configured for the AI provider.\n"), out);
                assertTrue(out.contains("| Detail: ANTHROPIC_API_KEY is not set\n"), out);
                assertTrue(out.contains("The original test failure is unchanged and still decides the build result."), out);
                String metrics = metricsFor(out, "com.acme.shop.OrderServiceTest#test1");
                assertTrue(metrics.contains("status=FAILED reason=MISSING_API_KEY attempts=0 "), metrics);
                assertTrue(out.contains("| [triage-metrics] summary status=FAILED failures=1 analyzed=1 skipped=0 succeeded=0 failed=1 limit=5 "), out);
            }
        });
    }

    @Test
    void aSuccessfulDiagnosisIsRenderedAndMeasured() throws Exception {
        TestSupport.withTempDir(reports -> {
            writeReport(reports, "com.acme.shop.OrderServiceTest", methods(1), "Cannot ship", "log line");
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of("ANTHROPIC_API_KEY", API_KEY), Map.of());

                assertEquals(0, run.exit());
                assertEquals(1, provider.hits());
                String out = run.output();
                assertTrue(out.contains("| Probable cause (hypothesis):\n|   ship() does not validate the current state\n"), out);
                assertFalse(out.contains("TRIAGE FAILED"), out);
                String metrics = metricsFor(out, "com.acme.shop.OrderServiceTest#test1");
                assertTrue(metrics.contains("status=SUCCESS reason=none attempts=1 "), metrics);
                assertTrue(metrics.contains("tokensIn=1234 tokensOut=321 "), metrics);
                assertTrue(metrics.contains("context=TEST_SOURCE:1,CITED_SOURCE:1,"), metrics);   // categorias de contexto
                assertTrue(metrics.matches(".*contextArtifacts=[1-9]\\d* .*contextChars=[1-9]\\d* .*"), metrics);
                assertTrue(metrics.matches(".*durationMs=\\d+ .*"), metrics);
                assertTrue(out.contains("summary status=SUCCESS failures=1 analyzed=1 skipped=0 succeeded=1 failed=0 "), out);
            }
        });
    }

    @Test
    void anUnavailableProviderEndsAsTriageFailedAfterTwoAttemptsAndIsMeasured() throws Exception {
        TestSupport.withTempDir(reports -> {
            writeReport(reports, "com.acme.shop.OrderServiceTest", methods(1), "Cannot ship", "log");
            try (FakeProvider provider = new FakeProvider(FakeProvider.status(503, "{}"))) {
                Run run = run(reports, provider, Map.of("ANTHROPIC_API_KEY", API_KEY), Map.of());
                assertEquals(0, run.exit());
                assertEquals(2, provider.hits());
                assertTrue(run.output().contains("| TRIAGE FAILED\n"));
                assertTrue(metricsFor(run.output(), "com.acme.shop.OrderServiceTest#test1").contains("reason=PROVIDER_ERROR attempts=2 "));
            }
        });
    }

    @Test
    void oneFailedAnalysisDoesNotStopTheOthers() throws Exception {
        TestSupport.withTempDir(reports -> {
            writeReport(reports, "com.acme.shop.OrderServiceTest", methods(3), "Cannot ship", "log");
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID), FakeProvider.status(401, "{}"), FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of("ANTHROPIC_API_KEY", API_KEY), Map.of());
                assertEquals(3, provider.hits());
                assertEquals(2, count(run.output(), "| Probable cause (hypothesis):"));
                assertEquals(1, count(run.output(), "| TRIAGE FAILED"));
                assertTrue(run.output().contains("reason=INVALID_CREDENTIALS attempts=1 "));
                assertTrue(run.output().contains("summary status=FAILED failures=3 analyzed=3 skipped=0 succeeded=2 failed=1"));
            }
        });
    }

    // ---------- T11: no maximo 5 falhas distintas, sem agrupamento ----------

    @Test
    void exactlyFiveOfSevenFailuresAreAnalyzedAndTheRestAreListedAsSkipped() throws Exception {
        TestSupport.withTempDir(reports -> {
            writeReport(reports, "com.acme.shop.OrderServiceTest", methods(7), "Cannot ship", "log");
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of("ANTHROPIC_API_KEY", API_KEY), Map.of());
                String out = run.output();

                assertEquals(5, provider.hits(), "exactly the maximum is sent to the provider");
                assertEquals(5, count(out, "| Probable cause (hypothesis):"));
                assertTrue(out.contains("| Failed tests found: 7\n"), out);
                assertTrue(out.contains("| Analyzed: 5 (V1 limit: 5 distinct failed tests per pipeline)\n"), out);
                assertTrue(out.contains("| Skipped: 2 (not analyzed because of the V1 limit)\n|   - com.acme.shop.OrderServiceTest#test6\n|   - com.acme.shop.OrderServiceTest#test7\n"), out);
                assertFalse(out.contains("Test: com.acme.shop.OrderServiceTest#test6"), "a skipped failure must not be analyzed");
                assertTrue(out.contains("summary status=SUCCESS failures=7 analyzed=5 skipped=2 succeeded=5 failed=0 limit=5 "), out);
            }
        });
    }

    @Test
    void exactlyAtTheLimitNothingIsSkippedAndTheLimitIsConfigurableDownwards() throws Exception {
        TestSupport.withTempDir(reports -> {
            writeReport(reports, "com.acme.shop.OrderServiceTest", methods(5), "Cannot ship", "log");
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of("ANTHROPIC_API_KEY", API_KEY), Map.of());
                assertEquals(5, provider.hits());
                assertTrue(run.output().contains("| Skipped: 0\n"), run.output());
            }
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of("ANTHROPIC_API_KEY", API_KEY), Map.of("triage.max-failures", "2"));
                assertEquals(2, provider.hits());
                assertTrue(run.output().contains("analyzed=2 skipped=3 "), run.output());
            }
        });
    }

    @Test
    void similarFailuresAreNotGroupedOrDeduplicated() throws Exception {
        TestSupport.withTempDir(reports -> {
            // mesma mensagem, mesma pilha: continuam a ser 3 falhas distintas (so repeticoes exatas do mesmo teste caem)
            writeReport(reports, "com.acme.shop.OrderServiceTest", methods(3), "Same message", "same log");
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of("ANTHROPIC_API_KEY", API_KEY), Map.of());
                assertEquals(3, provider.hits());
                assertTrue(run.output().contains("failures=3 analyzed=3 skipped=0"), run.output());
            }
        });
    }

    // ---------- sem falhas / desligado / relatorio ilegivel ----------

    @Test
    void withNoFailedTestsNothingIsSentAndTheSummaryStatesIt() throws Exception {
        TestSupport.withTempDir(reports -> {
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of("ANTHROPIC_API_KEY", API_KEY), Map.of());
                assertEquals(0, provider.hits());
                assertTrue(run.output().contains("nothing to analyze"));
                assertTrue(run.output().contains("summary status=NO_FAILURES failures=0 analyzed=0 skipped=0"));
            }
        });
    }

    @Test
    void anUnreadableReportIsReportedAsAWarningWithoutStoppingTheOthers() throws Exception {
        TestSupport.withTempDir(reports -> {
            writeReport(reports, "com.acme.shop.OrderServiceTest", methods(1), "Cannot ship", "log");
            Files.writeString(reports.resolve("TEST-broken.xml"), "<testsuite><not closed");
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of("ANTHROPIC_API_KEY", API_KEY), Map.of());
                assertEquals(1, provider.hits());
                assertTrue(run.output().contains("| Warning: Could not read test report TEST-broken.xml"), run.output());
            }
        });
    }

    // ---------- T12 + T05: nenhum segredo conhecido no input do provider nem na saida de observabilidade ----------

    private static Map<String, String> secretEnv() {
        return Map.of("ANTHROPIC_API_KEY", API_KEY, "DB_PASSWORD", ENV_SECRET);
    }

    /** Falha cheia de segredos em todos os campos que o pipeline le: id do teste, mensagem, pilha e log. */
    private static void writeHostileReport(Path reports) throws Exception {
        String hostileMethod = "ships_" + GITHUB_TOKEN;
        String message = "password=" + DB_PASSWORD + " key " + API_KEY;
        String log = "connecting with " + ENV_SECRET + "\nAuthorization: Bearer " + BEARER + "\nclient " + API_KEY;
        writeReport(reports, "com.acme.shop.OrderServiceTest", List.of(hostileMethod), message, log);
    }

    @Test
    void noKnownSecretReachesTheProviderInputOrAnyOutputEvenWhenTheProviderEchoesThem() throws Exception {
        String echoError = "{\"error\":\"" + API_KEY + " " + DB_PASSWORD + " " + ENV_SECRET + " " + GITHUB_TOKEN + " " + BEARER + "\"}";
        java.util.Map<String, Object> hostileAnswer = SampleDiagnosis.valid();
        hostileAnswer.put("probableCause", "the log shows " + API_KEY + " and password=" + DB_PASSWORD);
        hostileAnswer.put("evidence", List.of("Bearer " + BEARER, ENV_SECRET));
        TestSupport.withTempDir(reports -> {
            writeHostileReport(reports);
            try (FakeProvider provider = new FakeProvider(FakeProvider.status(500, echoError), FakeProvider.ok(SampleDiagnosis.json(hostileAnswer)))) {
                Run run = run(reports, provider, secretEnv(), Map.of());

                // a observabilidade existe (nao e uma prova vazia) e traz o contexto contado, nao o conteudo
                String out = run.output();
                assertTrue(out.contains("[triage-metrics] status=SUCCESS reason=none attempts=2 "), out);
                assertTrue(out.contains("[triage-metrics] summary status=SUCCESS"), out);
                assertTrue(out.contains("| provider: attempt 1/2 failed: PROVIDER_ERROR"), "provider log must be part of the checked output: " + out);
                assertTrue(out.contains("[REDACTED]"), out);

                for (String secret : ALL_SECRETS) {
                    assertFalse(out.contains(secret), "secret reached the output (log/metrics/diagnosis)");
                    for (FakeProvider.Seen seen : provider.seen()) {
                        assertFalse(seen.body().contains(secret), "secret reached the provider input");
                    }
                }
                assertEquals(2, provider.seen().size());
            }
        });
    }

    @Test
    void noKnownSecretReachesTheOutputInTheFailurePathsEither() throws Exception {
        String echoError = "{\"error\":\"" + API_KEY + " " + DB_PASSWORD + " " + ENV_SECRET + " " + GITHUB_TOKEN + " " + BEARER + "\"}";
        for (FakeProvider.Reply reply : List.of(FakeProvider.status(401, echoError), FakeProvider.status(400, echoError),
                FakeProvider.status(503, echoError), FakeProvider.ok("echo " + API_KEY + " " + DB_PASSWORD))) {
            TestSupport.withTempDir(reports -> {
                writeHostileReport(reports);
                try (FakeProvider provider = new FakeProvider(reply)) {
                    Run run = run(reports, provider, secretEnv(), Map.of());
                    assertTrue(run.output().contains("TRIAGE FAILED"), run.output());
                    for (String secret : ALL_SECRETS) {
                        assertFalse(run.output().contains(secret), "secret in output for HTTP " + reply.status());
                        for (FakeProvider.Seen seen : provider.seen()) {
                            assertFalse(seen.body().contains(secret));
                        }
                    }
                }
            });
        }
        // e sem chave: o ambiente continua a ter segredos que nao podem sair
        TestSupport.withTempDir(reports -> {
            writeHostileReport(reports);
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of("DB_PASSWORD", ENV_SECRET), Map.of());
                assertTrue(run.output().contains("MISSING_API_KEY"));
                for (String secret : ALL_SECRETS) {
                    assertFalse(run.output().contains(secret));
                }
            }
        });
    }

    // ---------- injecao de comandos do Actions por nomes de teste e mensagens ----------

    @Test
    void aHostileTestNameCannotInjectWorkflowCommandsThroughTheMetricsOrOverview() throws Exception {
        TestSupport.withTempDir(reports -> {
            writeReport(reports, "com.acme.shop.OrderServiceTest", List.of("::error::pwned", "##[error]x"), "m", "l");
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                Run run = run(reports, provider, Map.of("ANTHROPIC_API_KEY", API_KEY), Map.of("triage.max-failures", "1"));
                for (String line : run.output().split("\n")) {
                    if (!line.isEmpty()) {
                        assertTrue(line.startsWith("|") || line.startsWith("AI TEST TRIAGE"), "unprefixed line: " + line);
                        assertFalse(line.stripLeading().startsWith("::") || line.stripLeading().startsWith("##["), line);
                    }
                }
            }
        });
    }

    // ---------- isolamento por falha: um erro interno nao esconde as outras analises nem o resumo ----------

    @Test
    void aContextErrorAndAnInternalErrorAreReportedPerFailureAndTheRestStillRun() throws Exception {
        TestSupport.withTempDir(reports -> {
            writeReport(reports, "com.acme.shop.OrderServiceTest", methods(3), "Cannot ship", "log");
            try (FakeProvider provider = new FakeProvider(FakeProvider.ok(VALID))) {
                TriageConfig withUrl = TestSupport.sampleConfig(Map.of("triage.reports-dir", reports.toString(),
                        "triage.provider.base-url", provider.baseUrl(), "triage.provider.retry-delay-millis", "0"));
                int[] call = {0};
                var real = new ContextBuilder(withUrl,
                        new DeterministicRetriever(withUrl,
                                DocumentMap.load(withUrl.docMapFile())));
                Function<FailureInput, ContextPackage> flaky = failure -> {
                    call[0]++;
                    if (call[0] == 1) {
                        throw new IllegalStateException("secret " + API_KEY);
                    }
                    if (call[0] == 2) {
                        return null;
                    }
                    return real.build(failure);
                };
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int exit = new TriagePipeline(withUrl, Map.of("ANTHROPIC_API_KEY", API_KEY),
                        new PrintStream(buffer, true, StandardCharsets.UTF_8), flaky).run();
                String out = buffer.toString(StandardCharsets.UTF_8);

                assertEquals(0, exit);
                assertEquals(1, provider.hits(), "only the third failure reaches the provider");
                assertTrue(out.contains("| Reason: Could not build the context for this failure.\n| Detail: IllegalStateException\n"), out);
                assertTrue(out.contains("reason=CONTEXT_ERROR attempts=0 "), out);
                assertTrue(out.contains("reason=INTERNAL_ERROR attempts=0 "), out);
                assertTrue(out.contains("status=SUCCESS reason=none attempts=1 "), out);
                assertTrue(out.contains("summary status=FAILED failures=3 analyzed=3 skipped=0 succeeded=1 failed=2"), out);
                assertFalse(out.contains(API_KEY), "exception messages must never be printed");
            }
        });
    }

    @Test
    void theProviderLogSinkPrefixesMasksAndFlattensEveryLine() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        var safe = new SafeText(
                new SecretFilter(Map.of()));
        TriagePipeline.logSink(out, safe).accept("key=" + API_KEY + "\n::error::pwned " + GITHUB_TOKEN);
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertEquals(1L, text.lines().count(), text);
        assertTrue(text.startsWith("| "), text);
        assertFalse(text.contains(API_KEY) || text.contains(GITHUB_TOKEN), text);
    }
}
