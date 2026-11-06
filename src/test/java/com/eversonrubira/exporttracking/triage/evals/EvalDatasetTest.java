package com.eversonrubira.exporttracking.triage.evals;

import com.eversonrubira.exporttracking.triage.TriageApplication;
import com.eversonrubira.exporttracking.triage.collector.CollectionResult;
import com.eversonrubira.exporttracking.triage.collector.FailureCollector;
import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import com.eversonrubira.exporttracking.triage.context.ContextBuilder;
import com.eversonrubira.exporttracking.triage.context.ContextPackage;
import com.eversonrubira.exporttracking.triage.context.DeterministicRetriever;
import com.eversonrubira.exporttracking.triage.context.DocumentMap;
import com.eversonrubira.exporttracking.triage.security.SecretFilter;
import com.eversonrubira.exporttracking.triage.validation.Diagnosis;
import com.eversonrubira.exporttracking.triage.validation.DiagnosisValidator;
import com.eversonrubira.exporttracking.triage.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T13: o dataset congelado de 10 falhas Java com causa conhecida.
 *
 * Estes testes nao chamam o Claude. Provam que o dataset esta bem formado, que cobre os
 * cenarios pedidos e que pode ser executado repetidamente contra a mesma implementacao do
 * triage (a mesma falha produz sempre o mesmo contexto). NAO medem a qualidade de nenhum
 * diagnostico real: isso e o T14.
 */
class EvalDatasetTest {

    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    private static final String GENERIC_DIAGNOSIS = """
            {"probableCause":"The test failed because of an unexpected error in the application.",
             "evidence":["The test failed."],
             "relatedDocuments":[],
             "possibleSolutions":[{"solution":"Investigate the failure and fix the code.","tradeoffs":["Takes time"]}],
             "confidence":"HIGH",
             "confidenceReason":"The test failed.",
             "limitations":[]}""";

    /** Mesma configuracao para todos os casos: so o projeto muda. */
    private static TriageConfig config(EvalCase c) {
        Properties p = new Properties();
        p.setProperty("triage.project-root", c.projectDir().toString());
        p.setProperty("triage.project-packages", "com.acme.export");
        p.setProperty("triage.reports-dir", "reports");
        p.setProperty("triage.doc-map", "doc-map.properties");
        return TriageConfig.fromProperties(p);
    }

    private static List<FailureInput> failures(TriageConfig config) {
        CollectionResult result = new FailureCollector(config.maxLogLines()).collect(config.reportsDir());
        assertTrue(result.warnings().isEmpty(), "unreadable reports: " + result.warnings());
        return result.failures();
    }

    private static ContextPackage context(TriageConfig config, FailureInput failure) {
        DeterministicRetriever retriever = new DeterministicRetriever(config, DocumentMap.load(config.docMapFile()));
        return new ContextBuilder(config, retriever).build(failure);
    }

    @Test
    void thereAreTenWellFormedCases() throws IOException {
        List<EvalCase> cases = EvalCase.loadAll();
        assertEquals(10, cases.size());

        Set<String> ids = new HashSet<>();
        for (EvalCase c : cases) {
            String where = c.id();
            assertTrue(ids.add(c.id()), "duplicated id " + where);
            assertEquals(c.dir().getFileName().toString(), c.id(), "directory name must be the id");
            assertFalse(c.title().isBlank(), where);
            assertTrue(DIFFICULTIES.contains(c.difficulty()), where + " difficulty");
            assertFalse(c.categories().isEmpty(), where + " categories");
            assertTrue(c.failedTest().matches("[\\w.]+#\\w+"), where + " failedTest");
            // Os 5 itens pedidos para cada caso.
            assertFalse(c.presentedFailure().isBlank(), where + " presentedFailure");
            assertFalse(c.knownRootCause().isBlank(), where + " knownRootCause");
            assertTrue(c.expectedEvidence().size() >= 3, where + " expectedEvidence");
            assertFalse(c.usefulness().mustMention().isEmpty(), where + " objective criteria");
            c.usefulness().mustMention().forEach(group -> assertFalse(group.isEmpty(), where + " empty alternative group"));
            assertFalse(c.usefulness().manualChecks().isEmpty(), where + " manualChecks");
            assertFalse(c.answerability().isBlank(), where + " answerability");
            // O spec/ADR relacionado, quando existe, e um ficheiro real do projeto do caso.
            for (String spec : c.relatedSpecs()) {
                String file = spec.contains("#") ? spec.substring(0, spec.indexOf('#')) : spec;
                assertTrue(Files.isRegularFile(c.projectDir().resolve(file)), where + " missing document " + file);
            }
            assertTrue(Files.isRegularFile(c.referenceDiagnosis()), where + " reference diagnosis");
        }
    }

    @Test
    void theSetCoversTheRequestedScenarios() throws IOException {
        List<EvalCase> cases = EvalCase.loadAll();
        Set<String> all = new HashSet<>();
        cases.forEach(c -> all.addAll(c.categories()));
        for (String required : List.of("INSUFFICIENT_CONTEXT", "DOC_RELEVANT", "DOC_ABSENT", "PROMPT_INJECTION", "NO_HIGH_CONFIDENCE")) {
            assertTrue(all.contains(required), "no case covers " + required);
        }
        // Causas tecnicamente diferentes: a 1.a categoria de cada caso e o tipo da causa e nao se repete.
        Set<String> causeKinds = new HashSet<>();
        for (EvalCase c : cases) {
            assertTrue(causeKinds.add(c.categories().get(0)), "cause kind repeated: " + c.categories().get(0));
        }
        assertEquals(10, causeKinds.size());
        // Nem so casos faceis.
        assertTrue(cases.stream().anyMatch(c -> c.difficulty().equals("HARD")));
        assertTrue(cases.stream().anyMatch(c -> c.difficulty().equals("EASY")));
        // O caso de contexto insuficiente e o de "sem HIGH" tem de limitar a confianca.
        for (EvalCase c : cases) {
            if (c.categories().contains("NO_HIGH_CONFIDENCE")) {
                assertEquals("MEDIUM", c.usefulness().maxConfidence(), c.id());
            }
        }
    }

    @Test
    void eachCaseHasExactlyOneFrozenFailureMatchingTheCase() throws IOException {
        for (EvalCase c : EvalCase.loadAll()) {
            List<FailureInput> failures = failures(config(c));
            assertEquals(1, failures.size(), c.id());
            assertEquals(c.failedTest(), failures.get(0).testId(), c.id());
        }
    }

    @Test
    void theContextIsRepeatableAndMatchesTheFrozenExpectation() throws IOException {
        for (EvalCase c : EvalCase.loadAll()) {
            TriageConfig config = config(c);
            FailureInput failure = failures(config).get(0);

            ContextPackage first = context(config, failure);
            ContextPackage second = context(config, failure);
            assertEquals(first.toInspectableText(), second.toInspectableText(), c.id() + " context must be repeatable");

            List<String> actual = first.artifacts().stream().map(a -> a.kind() + " " + a.path()).toList();
            assertEquals(c.expectedArtifacts(), actual, c.id() + " retrieved artifacts");
            for (String expected : c.expectedLimitationsContain()) {
                assertTrue(first.limitations().stream().anyMatch(l -> l.contains(expected)),
                        c.id() + " missing declared limitation: " + expected);
            }
        }
    }

    @Test
    void theReferenceDiagnosisIsValidAndMeetsItsOwnCriteria() throws IOException {
        for (EvalCase c : EvalCase.loadAll()) {
            ValidationResult result = DiagnosisValidator.validate(Files.readString(c.referenceDiagnosis()));
            assertTrue(result.isValid(), c.id() + " reference is not a valid diagnosis: " + result.errors());
            List<String> violations = UsefulnessCriteria.violations(c, result.diagnosis());
            assertTrue(violations.isEmpty(), c.id() + " reference violates its own criteria: " + violations);
        }
    }

    @Test
    void aGenericConfidentDiagnosisNeverMeetsTheCriteria() throws IOException {
        ValidationResult generic = DiagnosisValidator.validate(GENERIC_DIAGNOSIS);
        assertTrue(generic.isValid(), String.valueOf(generic.errors()));
        Diagnosis diagnosis = generic.diagnosis();
        for (EvalCase c : EvalCase.loadAll()) {
            assertFalse(UsefulnessCriteria.violations(c, diagnosis).isEmpty(),
                    c.id() + ": criteria must reject a diagnosis that says nothing specific");
        }
    }

    @Test
    void criteriaRejectTheKnownWrongDirections() throws IOException {
        // Mutacoes dirigidas ao que cada criterio promete proteger.
        for (EvalCase c : EvalCase.loadAll()) {
            Diagnosis good = DiagnosisValidator.validate(Files.readString(c.referenceDiagnosis())).diagnosis();
            if (c.usefulness().maxConfidence().equals("MEDIUM")) {
                Diagnosis overconfident = new Diagnosis(good.probableCause(), good.evidence(), good.relatedDocuments(),
                        good.possibleSolutions(), com.eversonrubira.exporttracking.triage.validation.Confidence.HIGH,
                        good.confidenceReason(), good.limitations());
                assertFalse(UsefulnessCriteria.violations(c, overconfident).isEmpty(), c.id() + " HIGH must be rejected");
            }
            Diagnosis invented = new Diagnosis(good.probableCause(), good.evidence(),
                    List.of("docs/DOES-NOT-EXIST.md"), good.possibleSolutions(), good.confidence(),
                    good.confidenceReason(), good.limitations());
            assertFalse(UsefulnessCriteria.violations(c, invented).isEmpty(), c.id() + " invented document must be rejected");
        }
    }

    @Test
    void theSecretFixtureIsInTheReportButNeverInTheSanitizedContext() throws IOException {
        int checked = 0;
        for (EvalCase c : EvalCase.loadAll()) {
            if (c.secretFixture() == null) {
                continue;
            }
            checked++;
            TriageConfig config = config(c);
            FailureInput failure = failures(config).get(0);
            assertTrue(failure.logExcerpt().contains(c.secretFixture()), c.id() + ": the fixture must really be in the frozen log");

            ContextPackage pkg = context(config, failure);
            String sent = new SecretFilter(Map.of()).sanitize(pkg).context().toInspectableText();
            assertFalse(sent.contains(c.secretFixture()), c.id() + ": the secret reached the context sent to the provider");
            assertTrue(sent.contains(SecretFilter.MASK), c.id() + ": the log line should show the mask");
        }
        assertTrue(checked >= 1);
    }

    @Test
    void theWholePipelineCanBeRunRepeatedlyWithoutAnApiKey() throws IOException {
        for (EvalCase c : EvalCase.loadAll()) {
            TriageConfig config = config(c);
            String first = runPipeline(config);
            String second = runPipeline(config);
            assertEquals(first, second, c.id() + " pipeline output must be repeatable");
            assertTrue(first.contains("MISSING_API_KEY"), c.id());
            assertTrue(first.contains(c.failedTest()), c.id());
            if (c.secretFixture() != null) {
                assertFalse(first.contains(c.secretFixture()), c.id() + " secret in the pipeline output");
            }
        }
    }

    /** Saida do pipeline sem chave de API. As duracoes variam entre execucoes e sao normalizadas. */
    private static String runPipeline(TriageConfig config) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            assertEquals(0, TriageApplication.run(config, Map.of(), out));
        }
        return bytes.toString(StandardCharsets.UTF_8).replaceAll("durationMs=\\d+", "durationMs=N");
    }
}
