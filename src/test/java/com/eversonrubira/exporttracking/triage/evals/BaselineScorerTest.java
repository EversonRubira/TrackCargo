package com.eversonrubira.exporttracking.triage.evals;

import com.eversonrubira.exporttracking.triage.validation.Diagnosis;
import com.eversonrubira.exporttracking.triage.validation.DiagnosisValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T14: a logica de pontuacao do baseline, sem rede e sem Claude.
class BaselineScorerTest {

    private static Diagnosis reference(EvalCase c) throws IOException {
        return DiagnosisValidator.validate(Files.readString(c.referenceDiagnosis())).diagnosis();
    }

    private static BaselineScorer.CaseResult ok(EvalCase c, Diagnosis d, int in, int out, long ms) {
        return new BaselineScorer.CaseResult(c.id(), true, "none", "", 1, ms, in, out, d);
    }

    private static BaselineScorer.CaseResult failed(EvalCase c, String reason, int attempts, int in, int out) {
        return new BaselineScorer.CaseResult(c.id(), false, reason, "detail", attempts, 1000, in, out, null);
    }

    @Test
    void costUsesTheConfirmedOfficialPrices() {
        // O custo medido no smoke test: 2619 tokens de entrada e 1471 de saida.
        assertEquals(0.019948, BaselineScorer.cost(2619, 1471), 1e-9);
        assertEquals(2.0, BaselineScorer.cost(1_000_000, 0), 1e-9);
        assertEquals(10.0, BaselineScorer.cost(0, 1_000_000), 1e-9);
    }

    @Test
    void theWorstCaseOfOneCaseMatchesTheApprovedDesign() {
        assertEquals(0.052, BaselineRunner.WORST_CASE_PER_CASE_USD, 1e-9);
        assertEquals(0.60, BaselineRunner.CAP_USD, 1e-9);
        assertEquals(20, 10 * 2);   // 10 casos x 2 tentativas: o teto de chamadas aprovado
    }

    @Test
    void aReferenceDiagnosisIsAutoPassAndAFailedCallIsNotEvaluated() throws IOException {
        for (EvalCase c : EvalCase.loadAll()) {
            BaselineScorer.Scored good = BaselineScorer.score(c, ok(c, reference(c), 2000, 1000, 5000));
            assertEquals(BaselineScorer.Auto.AUTO_PASS, good.auto(), c.id() + " " + good.violations());
            BaselineScorer.Scored none = BaselineScorer.score(c, failed(c, "TIMEOUT", 2, -1, -1));
            assertEquals(BaselineScorer.Auto.NOT_EVALUATED, none.auto(), c.id());
            assertFalse(none.costKnown());
        }
    }

    @Test
    void anOverconfidentOrInventedDiagnosisIsAutoFailWithTheViolationsListed() throws IOException {
        EvalCase c08 = EvalCase.loadAll().stream().filter(c -> c.id().startsWith("case-08")).findFirst().orElseThrow();
        Diagnosis good = reference(c08);
        Diagnosis overconfident = new Diagnosis(good.probableCause(), good.evidence(), List.of("docs/INVENTED.md"),
                good.possibleSolutions(), com.eversonrubira.exporttracking.triage.validation.Confidence.HIGH,
                good.confidenceReason(), List.of());
        BaselineScorer.Scored scored = BaselineScorer.score(c08, ok(c08, overconfident, 1000, 500, 1000));
        assertEquals(BaselineScorer.Auto.AUTO_FAIL, scored.auto());
        assertTrue(scored.violations().size() >= 3, String.valueOf(scored.violations()));
    }

    @Test
    void theRetrievalGapGroupIsTheFiveKnownCases() throws IOException {
        List<String> gap = new ArrayList<>();
        for (EvalCase c : EvalCase.loadAll()) {
            if (BaselineScorer.isGapCase(c.id())) {
                gap.add(c.id().substring(0, 7));
            }
        }
        assertEquals(List.of("case-03", "case-05", "case-06", "case-08", "case-09"), gap);
    }

    @Test
    void summaryConsolidatesTokensCostLatencyAndTheGapGroup() throws IOException {
        List<BaselineScorer.Scored> scored = new ArrayList<>();
        long latency = 1000;
        for (EvalCase c : EvalCase.loadAll()) {
            scored.add(BaselineScorer.score(c, ok(c, reference(c), 2000, 1000, latency)));
            latency += 1000;   // 1000, 2000, ..., 10000
        }
        BaselineScorer.Summary s = BaselineScorer.summarize(scored);
        assertEquals(10, s.cases());
        assertEquals(10, s.autoPass());
        assertEquals(5, s.gapCases());
        assertEquals(5, s.gapAutoPass());
        assertEquals(5, s.otherCases());
        assertEquals(20_000, s.tokensIn());
        assertEquals(10_000, s.tokensOut());
        assertEquals(10 * BaselineScorer.cost(2000, 1000), s.costUsd(), 1e-9);
        assertEquals(5500L, s.latencyMeanMillis());
        assertEquals(6000L, s.latencyMedianMillis());
        assertEquals(10_000L, s.latencyMaxMillis());
        assertEquals(10, s.attempts());
        assertEquals(0, s.casesWithUnknownTokens());
    }

    @Test
    void attemptsWithUnknownTokensAreCountedSeparatelyAndNotAsZeroCost() throws IOException {
        EvalCase c = EvalCase.loadAll().get(0);
        BaselineScorer.Summary s = BaselineScorer.summarize(List.of(BaselineScorer.score(c, failed(c, "TIMEOUT", 2, -1, -1))));
        assertEquals(1, s.casesWithUnknownTokens());
        assertEquals(0.0, s.costUsd(), 0.0);
        assertEquals(2, s.attempts());
    }

    @Test
    void theStrictGoalNeedsEightUsefulAndOverridesAreReportedSeparately() throws IOException {
        List<EvalCase> cases = EvalCase.loadAll();
        List<BaselineScorer.Scored> scored = new ArrayList<>();
        for (EvalCase c : cases) {
            scored.add(BaselineScorer.score(c, ok(c, reference(c), 2000, 1000, 1000)));
        }
        Map<String, BaselineScorer.Human> verdicts = new HashMap<>();
        cases.forEach(c -> verdicts.put(c.id(), BaselineScorer.Human.USEFUL));
        BaselineScorer.Goal all = BaselineScorer.goal(scored, verdicts);
        assertEquals(10, all.strictUseful());
        assertTrue(all.metStrict());

        // Um caso falhado (TIMEOUT) e dois AUTO_FAIL que o humano considera uteis (sobreposicoes):
        // 7 uteis estritos, 2 sobreposicoes. A meta estrita NAO e atingida; "com sobreposicoes" (9) sim.
        Diagnosis generic = DiagnosisValidator.validate("""
                {"probableCause":"An unexpected error.","evidence":["failed"],"relatedDocuments":[],
                 "possibleSolutions":[{"solution":"Fix it.","tradeoffs":["time"]}],
                 "confidence":"HIGH","confidenceReason":"failed","limitations":[]}""").diagnosis();
        List<BaselineScorer.Scored> mixed = new ArrayList<>(scored);
        mixed.set(0, BaselineScorer.score(cases.get(0), ok(cases.get(0), generic, 1, 1, 1)));
        mixed.set(1, BaselineScorer.score(cases.get(1), failed(cases.get(1), "TIMEOUT", 2, -1, -1)));
        mixed.set(2, BaselineScorer.score(cases.get(2), ok(cases.get(2), generic, 1, 1, 1)));
        BaselineScorer.Goal g = BaselineScorer.goal(mixed, verdicts);
        assertEquals(7, g.strictUseful());
        assertEquals(2, g.overrides());
        assertEquals(9, g.withOverrides());
        assertFalse(g.metStrict());
        assertTrue(g.metWithOverrides());
    }

    @Test
    void casesWithoutAHumanVerdictAreNotCountedYet() throws IOException {
        List<BaselineScorer.Scored> scored = new ArrayList<>();
        for (EvalCase c : EvalCase.loadAll()) {
            scored.add(BaselineScorer.score(c, ok(c, reference(c), 2000, 1000, 1000)));
        }
        BaselineScorer.Goal g = BaselineScorer.goal(scored, Map.of());
        assertEquals(0, g.strictUseful());
        assertEquals(10, g.pendingReview());
        assertFalse(g.metStrict());
    }
}
