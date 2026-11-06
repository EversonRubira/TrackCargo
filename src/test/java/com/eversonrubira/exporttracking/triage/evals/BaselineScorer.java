package com.eversonrubira.exporttracking.triage.evals;

import com.eversonrubira.exporttracking.triage.validation.Diagnosis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * T14: pontuacao do baseline. Logica pura, sem rede: recebe o resultado de cada caso e
 * calcula o score automatico, o custo, a consolidacao e a regra da meta 8/10.
 * Os criterios sao os congelados do dataset (UsefulnessCriteria); aqui nao se mudam.
 */
final class BaselineScorer {

    /** Precos oficiais confirmados: US$ por milhao de tokens. */
    static final double INPUT_USD_PER_MTOK = 2.0;
    static final double OUTPUT_USD_PER_MTOK = 10.0;
    static final int GOAL_USEFUL_CASES = 8;
    /** Casos em que o retrieval deterministico nao recupera a classe de producao (conhecido antes do baseline). */
    static final Set<String> RETRIEVAL_GAP_CASES = Set.of("case-03", "case-05", "case-06", "case-08", "case-09");

    private BaselineScorer() {
    }

    enum Auto { AUTO_PASS, AUTO_FAIL, NOT_EVALUATED }

    /** Veredicto humano final, por caso (quem decide e o Everson). */
    enum Human { USEFUL, NOT_USEFUL }

    /** O que aconteceu com um caso. {@code reason} e "none" no sucesso. */
    record CaseResult(String caseId, boolean succeeded, String reason, String detail, int attempts,
                      long latencyMillis, int tokensIn, int tokensOut, Diagnosis diagnosis) {
    }

    record Scored(EvalCase evalCase, CaseResult result, Auto auto, List<String> violations,
                  double costUsd, boolean costKnown) {
        boolean retrievalGap() {
            return isGapCase(evalCase.id());
        }
    }

    static boolean isGapCase(String caseId) {
        return RETRIEVAL_GAP_CASES.contains(caseId.substring(0, Math.min(7, caseId.length())));
    }

    static double cost(int tokensIn, int tokensOut) {
        return tokensIn * INPUT_USD_PER_MTOK / 1_000_000.0 + tokensOut * OUTPUT_USD_PER_MTOK / 1_000_000.0;
    }

    static Scored score(EvalCase c, CaseResult r) {
        boolean costKnown = r.tokensIn() >= 0 && r.tokensOut() >= 0;
        double cost = costKnown ? cost(r.tokensIn(), r.tokensOut()) : 0.0;
        if (!r.succeeded() || r.diagnosis() == null) {
            return new Scored(c, r, Auto.NOT_EVALUATED, List.of(), cost, costKnown);
        }
        List<String> violations = UsefulnessCriteria.violations(c, r.diagnosis());
        return new Scored(c, r, violations.isEmpty() ? Auto.AUTO_PASS : Auto.AUTO_FAIL, violations, cost, costKnown);
    }

    record Summary(int cases, int succeeded, int autoPass, int autoFail, int notEvaluated,
                   int gapCases, int gapAutoPass, int otherCases, int otherAutoPass,
                   int tokensIn, int tokensOut, double costUsd, int casesWithUnknownTokens, int attempts,
                   long latencyMeanMillis, long latencyMedianMillis, long latencyMaxMillis) {
    }

    static Summary summarize(List<Scored> scored) {
        int succeeded = 0, pass = 0, fail = 0, notEval = 0, gap = 0, gapPass = 0, other = 0, otherPass = 0;
        int in = 0, out = 0, unknown = 0, attempts = 0;
        double cost = 0;
        List<Long> latencies = new ArrayList<>();
        for (Scored s : scored) {
            if (s.result().succeeded()) {
                succeeded++;
            }
            switch (s.auto()) {
                case AUTO_PASS -> pass++;
                case AUTO_FAIL -> fail++;
                case NOT_EVALUATED -> notEval++;
            }
            if (s.retrievalGap()) {
                gap++;
                gapPass += s.auto() == Auto.AUTO_PASS ? 1 : 0;
            } else {
                other++;
                otherPass += s.auto() == Auto.AUTO_PASS ? 1 : 0;
            }
            attempts += s.result().attempts();
            if (s.result().attempts() > 0) {
                latencies.add(s.result().latencyMillis());
                if (s.costKnown()) {
                    in += s.result().tokensIn();
                    out += s.result().tokensOut();
                    cost += s.costUsd();
                } else {
                    unknown++;
                }
            }
        }
        latencies.sort(Long::compare);
        long mean = latencies.isEmpty() ? 0 : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0));
        long median = latencies.isEmpty() ? 0 : latencies.get(latencies.size() / 2);
        long max = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);
        return new Summary(scored.size(), succeeded, pass, fail, notEval, gap, gapPass, other, otherPass,
                in, out, cost, unknown, attempts, mean, median, max);
    }

    /**
     * Meta 8/10. Util (estrito) = SUCCESS e AUTO_PASS e veredicto humano USEFUL. As sobreposicoes
     * (AUTO_FAIL que o humano considera util) so contam na linha "com sobreposicoes".
     * Casos sem veredicto humano ainda nao contam e ficam em {@code pendingReview}.
     */
    record Goal(int strictUseful, int overrides, int withOverrides, int pendingReview, boolean metStrict, boolean metWithOverrides) {
    }

    static Goal goal(List<Scored> scored, Map<String, Human> verdicts) {
        int strict = 0, overrides = 0, pending = 0;
        for (Scored s : scored) {
            Human verdict = verdicts.get(s.evalCase().id());
            if (verdict == null) {
                pending++;
                continue;
            }
            if (!s.result().succeeded() || verdict != Human.USEFUL) {
                continue;
            }
            if (s.auto() == Auto.AUTO_PASS) {
                strict++;
            } else if (s.auto() == Auto.AUTO_FAIL) {
                overrides++;
            }
        }
        return new Goal(strict, overrides, strict + overrides, pending,
                strict >= GOAL_USEFUL_CASES, strict + overrides >= GOAL_USEFUL_CASES);
    }

    // ---------- serializacao simples (para os ficheiros de resultados) ----------

    static Map<String, Object> diagnosisToMap(Diagnosis d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("probableCause", d.probableCause());
        m.put("evidence", d.evidence());
        m.put("relatedDocuments", d.relatedDocuments());
        List<Object> solutions = new ArrayList<>();
        for (Diagnosis.Solution s : d.possibleSolutions()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("solution", s.solution());
            sm.put("tradeoffs", s.tradeoffs());
            solutions.add(sm);
        }
        m.put("possibleSolutions", solutions);
        m.put("confidence", d.confidence().name());
        m.put("confidenceReason", d.confidenceReason());
        m.put("limitations", d.limitations());
        return m;
    }

    static Map<String, Object> outcomeToMap(Scored s) {
        CaseResult r = s.result();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseId", r.caseId());
        m.put("succeeded", r.succeeded());
        m.put("reason", r.reason());
        m.put("detail", r.detail());
        m.put("attempts", r.attempts());
        m.put("latencyMillis", r.latencyMillis());
        m.put("tokensIn", r.tokensIn());
        m.put("tokensOut", r.tokensOut());
        m.put("costUsd", s.costKnown() ? Math.round(s.costUsd() * 1_000_000.0) / 1_000_000.0 : null);
        m.put("retrievalGapCase", s.retrievalGap());
        return m;
    }

    static Map<String, Object> scoreToMap(Scored s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseId", s.evalCase().id());
        m.put("auto", s.auto().name());
        m.put("violations", s.violations());
        return m;
    }
}
