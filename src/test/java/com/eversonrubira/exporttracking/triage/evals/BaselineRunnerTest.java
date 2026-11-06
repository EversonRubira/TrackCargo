package com.eversonrubira.exporttracking.triage.evals;

import com.eversonrubira.exporttracking.triage.TestSupport;
import com.eversonrubira.exporttracking.triage.provider.FakeProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T14: o runner do baseline contra um servidor local que faz de Claude. Nenhuma chamada real.
class BaselineRunnerTest {

    private static final String KEY = "sk-ant-api03-FAKE-KEY-FOR-TESTS-ONLY-1234567890";

    private static Properties against(FakeProvider provider) {
        Properties p = new Properties();
        p.setProperty("triage.provider.base-url", provider.baseUrl());
        p.setProperty("triage.provider.retry-delay-millis", "0");
        return p;
    }

    private static FakeProvider referencesInCaseOrder() throws Exception {
        List<FakeProvider.Reply> replies = new ArrayList<>();
        for (EvalCase c : EvalCase.loadAll()) {
            replies.add(FakeProvider.ok(Files.readString(c.referenceDiagnosis())));
        }
        return new FakeProvider(replies.toArray(new FakeProvider.Reply[0]));
    }

    private static String run(Path dir, FakeProvider provider, BaselineRunner.Limits limits, List<BaselineScorer.Scored> sink) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            sink.addAll(BaselineRunner.run(dir, Map.of("ANTHROPIC_API_KEY", KEY), against(provider), limits, out));
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void runsTenCasesInOrderWithOneCallEachAndPreservesEverything() throws Exception {
        TestSupport.withTempDir(dir -> {
            try (FakeProvider provider = referencesInCaseOrder()) {
                List<BaselineScorer.Scored> scored = new ArrayList<>();
                String log = run(dir, provider, BaselineRunner.Limits.defaults(), scored);

                assertEquals(10, provider.hits());
                assertEquals(10, scored.size());
                for (BaselineScorer.Scored s : scored) {
                    assertEquals(BaselineScorer.Auto.AUTO_PASS, s.auto(), s.evalCase().id() + " " + s.violations());
                    assertEquals(1234, s.result().tokensIn());
                    assertEquals(321, s.result().tokensOut());
                    for (String file : List.of("outcome.json", "context.txt", "diagnosis.json", "rendered.txt", "score.json")) {
                        assertTrue(Files.isRegularFile(dir.resolve(s.evalCase().id()).resolve(file)), s.evalCase().id() + "/" + file);
                    }
                }
                assertTrue(Files.isRegularFile(dir.resolve("baseline-results.json")));
                // os ficheiros tambem vao para o log, com prefixo "| " e marcadores
                assertTrue(log.contains("===BASELINE-FILE case-01-state-guard-off-by-one/diagnosis.json==="));
                assertTrue(log.contains("===BASELINE-FILE baseline-results.json==="));
                assertTrue(log.contains("auto=AUTO_PASS"));
            }
        });
    }

    @Test
    void theRequestSentIsTheSanitizedContextOfTheCaseAndTheKeyOnlyTravelsInTheHeader() throws Exception {
        TestSupport.withTempDir(dir -> {
            try (FakeProvider provider = referencesInCaseOrder()) {
                String log = run(dir, provider, BaselineRunner.Limits.defaults(), new ArrayList<>());
                FakeProvider.Seen first = provider.seen().get(0);
                assertEquals(KEY, first.headers().get("x-api-key"));
                assertTrue(first.body().contains("ConsignmentService"), "case 01 context must be in the request");
                assertFalse(first.body().contains(KEY));
                for (FakeProvider.Seen seen : provider.seen()) {
                    assertFalse(seen.body().contains(KEY));
                }
                // a chave nunca aparece no log nem nos ficheiros de resultados
                assertFalse(log.contains(KEY));
                try (var files = Files.walk(dir)) {
                    for (Path f : files.filter(Files::isRegularFile).toList()) {
                        assertFalse(Files.readString(f).contains(KEY), f.toString());
                    }
                }
            }
        });
    }

    @Test
    void theCaseFourSecretIsMaskedInThePreservedContext() throws Exception {
        TestSupport.withTempDir(dir -> {
            try (FakeProvider provider = referencesInCaseOrder()) {
                run(dir, provider, BaselineRunner.Limits.defaults(), new ArrayList<>());
                String context = Files.readString(dir.resolve("case-04-database-unreachable-in-ci/context.txt"));
                assertFalse(context.contains("Tr0ub4dor-fixture-only"));
                assertTrue(context.contains("[REDACTED]"));
            }
        });
    }

    @Test
    void aKeyEchoedByTheModelIsMaskedInTheLogAndInEveryPreservedFile() throws Exception {
        TestSupport.withTempDir(dir -> {
            List<FakeProvider.Reply> replies = new ArrayList<>();
            for (EvalCase c : EvalCase.loadAll()) {
                String json = Files.readString(c.referenceDiagnosis());
                // o modelo (hipotese hostil) repete a chave na causa provavel do primeiro caso
                replies.add(FakeProvider.ok(replies.isEmpty()
                        ? json.replace("\"probableCause\": \"", "\"probableCause\": \"leaked " + KEY + " ")
                        : json));
            }
            try (FakeProvider provider = new FakeProvider(replies.toArray(new FakeProvider.Reply[0]))) {
                String log = run(dir, provider, BaselineRunner.Limits.defaults(), new ArrayList<>());
                assertFalse(log.contains(KEY), "key in the log");
                assertTrue(Files.readString(dir.resolve("case-01-state-guard-off-by-one/diagnosis.json")).contains("[REDACTED]"));
                try (var files = Files.walk(dir)) {
                    for (Path f : files.filter(Files::isRegularFile).toList()) {
                        assertFalse(Files.readString(f).contains(KEY), f.toString());
                    }
                }
            }
        });
    }

    @Test
    void theBudgetGateStopsBeforeTheCaseThatCouldExceedTheCap() throws Exception {
        TestSupport.withTempDir(dir -> {
            try (FakeProvider provider = referencesInCaseOrder()) {
                List<BaselineScorer.Scored> scored = new ArrayList<>();
                // cada caso falso custa ~0,0057; com teto 0,06 correm 2 casos (0,0057 + 0,052 <= 0,06; 0,0114 + 0,052 > 0,06)
                run(dir, provider, new BaselineRunner.Limits(0.06, BaselineRunner.WALL_LIMIT_MILLIS), scored);
                assertEquals(2, provider.hits());
                assertEquals(BaselineScorer.Auto.AUTO_PASS, scored.get(0).auto());
                assertEquals("NOT_RUN_BUDGET", scored.get(2).result().reason());
                assertEquals(0, scored.get(2).result().attempts());
                assertEquals(BaselineScorer.Auto.NOT_EVALUATED, scored.get(9).auto());
            }
        });
    }

    @Test
    void theTimeGateStopsEverythingBeforeTheFirstCallWhenTheLimitIsAlreadyReached() throws Exception {
        TestSupport.withTempDir(dir -> {
            try (FakeProvider provider = referencesInCaseOrder()) {
                List<BaselineScorer.Scored> scored = new ArrayList<>();
                run(dir, provider, new BaselineRunner.Limits(BaselineRunner.CAP_USD, 0), scored);
                assertEquals(0, provider.hits());
                assertEquals("NOT_RUN_TIME", scored.get(0).result().reason());
            }
        });
    }

    @Test
    void aFailingCaseIsRecordedAndTheRunContinuesWithoutRetuningAnything() throws Exception {
        TestSupport.withTempDir(dir -> {
            List<FakeProvider.Reply> replies = new ArrayList<>();
            replies.add(FakeProvider.status(401, "{\"error\":{\"type\":\"authentication_error\"}}"));
            for (EvalCase c : EvalCase.loadAll().subList(1, 10)) {
                replies.add(FakeProvider.ok(Files.readString(c.referenceDiagnosis())));
            }
            try (FakeProvider provider = new FakeProvider(replies.toArray(new FakeProvider.Reply[0]))) {
                List<BaselineScorer.Scored> scored = new ArrayList<>();
                run(dir, provider, BaselineRunner.Limits.defaults(), scored);
                assertEquals("INVALID_CREDENTIALS", scored.get(0).result().reason());
                assertEquals(1, scored.get(0).result().attempts());   // credenciais nao se repetem
                assertEquals(BaselineScorer.Auto.NOT_EVALUATED, scored.get(0).auto());
                assertEquals(BaselineScorer.Auto.AUTO_PASS, scored.get(1).auto());
                assertEquals(10, provider.hits());
            }
        });
    }
}
