package com.eversonrubira.exporttracking.triage.evals;

import com.eversonrubira.exporttracking.triage.collector.CollectionResult;
import com.eversonrubira.exporttracking.triage.collector.FailureCollector;
import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import com.eversonrubira.exporttracking.triage.context.ContextBuilder;
import com.eversonrubira.exporttracking.triage.context.ContextPackage;
import com.eversonrubira.exporttracking.triage.context.DeterministicRetriever;
import com.eversonrubira.exporttracking.triage.context.DocumentMap;
import com.eversonrubira.exporttracking.triage.json.JsonSupport;
import com.eversonrubira.exporttracking.triage.provider.ClaudeClient;
import com.eversonrubira.exporttracking.triage.provider.ProviderOutcome;
import com.eversonrubira.exporttracking.triage.rendering.TriageRenderer;
import com.eversonrubira.exporttracking.triage.security.SanitizedContext;
import com.eversonrubira.exporttracking.triage.security.SecretFilter;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * T14: corre os casos congelados do dataset-v1 contra o Claude, um de cada vez, com os mesmos
 * componentes do pipeline (collector, retriever, filtro de segredos, cliente, validador) e a
 * configuracao de triage.properties. Nao altera nada: so le o dataset e escreve resultados.
 *
 * Limites duros (desenho aprovado): no maximo 2 tentativas por caso (config), teto de custo e
 * de tempo. Um caso que falha fica registado e a execucao continua. Nada e ajustado durante a corrida.
 *
 * Uso (so em CI, depois de aprovado): BaselineRunner <pasta-de-resultados>
 */
public final class BaselineRunner {

    static final double CAP_USD = 0.60;
    /** Pior caso de um caso: 2 tentativas x (3000 tokens de entrada + 2000 de saida). */
    static final double WORST_CASE_PER_CASE_USD = 2 * BaselineScorer.cost(3000, 2000);
    static final long WALL_LIMIT_MILLIS = 12 * 60 * 1000L;
    static final Path CONFIG_FILE = Path.of("docs/ai-test-triage/triage.properties");

    record Limits(double capUsd, long wallLimitMillis) {
        static Limits defaults() {
            return new Limits(CAP_USD, WALL_LIMIT_MILLIS);
        }
    }

    private BaselineRunner() {
    }

    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        run(Path.of(args[0]), System.getenv(), new Properties(), Limits.defaults(), out);
    }

    static List<BaselineScorer.Scored> run(Path outDir, Map<String, String> env, Properties extraOverrides,
                                           Limits limits, PrintStream log) throws IOException {
        SecretFilter filter = new SecretFilter(env);
        TriageRenderer renderer = new TriageRenderer(filter);
        Files.createDirectories(outDir);
        long started = System.nanoTime();
        double spent = 0;
        List<BaselineScorer.Scored> all = new ArrayList<>();

        log.println("| [baseline] start cap=" + limits.capUsd() + " wallLimitMillis=" + limits.wallLimitMillis());
        for (EvalCase c : EvalCase.loadAll()) {
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            String stop = null;
            if (elapsed >= limits.wallLimitMillis()) {
                stop = "NOT_RUN_TIME";
            } else if (spent + WORST_CASE_PER_CASE_USD > limits.capUsd()) {
                stop = "NOT_RUN_BUDGET";
            }
            BaselineScorer.CaseResult result;
            String context = "";
            String rendered = "";
            if (stop != null) {
                result = new BaselineScorer.CaseResult(c.id(), false, stop, "not executed", 0, 0, -1, -1, null);
            } else {
                Analysis a = analyze(c, env, extraOverrides, filter, renderer, log);
                result = a.result();
                context = a.context();
                rendered = a.rendered();
            }
            BaselineScorer.Scored scored = BaselineScorer.score(c, result);
            all.add(scored);
            if (result.attempts() > 0) {
                // tentativas sem tokens conhecidos contam pelo pior caso no tecto
                spent += scored.costKnown() ? scored.costUsd() : WORST_CASE_PER_CASE_USD;
            }
            write(outDir, filter, log, c.id() + "/outcome.json", JsonSupport.write(BaselineScorer.outcomeToMap(scored)));
            if (!context.isEmpty()) {
                write(outDir, filter, log, c.id() + "/context.txt", context);
            }
            if (result.diagnosis() != null) {
                write(outDir, filter, log, c.id() + "/diagnosis.json", JsonSupport.write(BaselineScorer.diagnosisToMap(result.diagnosis())));
            }
            if (!rendered.isEmpty()) {
                write(outDir, filter, log, c.id() + "/rendered.txt", rendered);
            }
            write(outDir, filter, log, c.id() + "/score.json", JsonSupport.write(BaselineScorer.scoreToMap(scored)));
            log.println("| [baseline] " + c.id() + " status=" + (result.succeeded() ? "SUCCESS" : result.reason())
                    + " auto=" + scored.auto() + " attempts=" + result.attempts() + " tokensIn=" + result.tokensIn()
                    + " tokensOut=" + result.tokensOut() + " latencyMs=" + result.latencyMillis()
                    + " spentUsd=" + String.format(java.util.Locale.ROOT, "%.4f", spent));
        }

        BaselineScorer.Summary summary = BaselineScorer.summarize(all);
        Map<String, Object> aggregate = new LinkedHashMap<>();
        List<Object> rows = new ArrayList<>();
        for (BaselineScorer.Scored s : all) {
            Map<String, Object> row = new LinkedHashMap<>(BaselineScorer.outcomeToMap(s));
            row.put("auto", s.auto().name());
            row.put("violations", s.violations());
            rows.add(row);
        }
        aggregate.put("cases", rows);
        aggregate.put("summary", summaryToMap(summary));
        write(outDir, filter, log, "baseline-results.json", JsonSupport.write(aggregate));
        log.println("| [baseline] done " + JsonSupport.write(summaryToMap(summary)));
        return all;
    }

    private record Analysis(BaselineScorer.CaseResult result, String context, String rendered) {
    }

    private static Analysis analyze(EvalCase c, Map<String, String> env, Properties extra, SecretFilter filter,
                                    TriageRenderer renderer, PrintStream log) {
        try {
            Properties overrides = new Properties();
            overrides.setProperty("triage.project-root", c.projectDir().toString());
            overrides.setProperty("triage.project-packages", "com.acme.export");
            overrides.setProperty("triage.reports-dir", "reports");
            overrides.setProperty("triage.doc-map", "doc-map.properties");
            overrides.putAll(extra);
            TriageConfig config = TriageConfig.load(CONFIG_FILE, overrides);

            CollectionResult collected = new FailureCollector(config.maxLogLines()).collect(config.reportsDir());
            if (collected.failures().isEmpty()) {
                return new Analysis(new BaselineScorer.CaseResult(c.id(), false, "NO_FAILURE_FOUND", "no failure in the frozen report",
                        0, 0, -1, -1, null), "", "");
            }
            FailureInput failure = collected.failures().get(0);
            ContextPackage pkg = new ContextBuilder(config,
                    new DeterministicRetriever(config, DocumentMap.load(config.docMapFile()))).build(failure);
            SanitizedContext sanitized = filter.sanitize(pkg);
            String context = sanitized.context().toInspectableText();

            ClaudeClient client = new ClaudeClient(config, env.get(config.apiKeyEnv()),
                    line -> log.println("| [provider " + c.id().substring(0, 7) + "] " + filter.mask(line).replace('\n', ' ')));
            ProviderOutcome outcome = client.diagnose(sanitized);
            String rendered = outcome.succeeded() ? renderer.success(failure.testId(), outcome.diagnosis())
                    : renderer.failure(failure.testId(), outcome);
            return new Analysis(new BaselineScorer.CaseResult(c.id(), outcome.succeeded(),
                    outcome.succeeded() ? "none" : outcome.reason().name(), outcome.detail() == null ? "" : outcome.detail(),
                    outcome.attempts(), outcome.elapsedMillis(), outcome.inputTokens(), outcome.outputTokens(),
                    outcome.diagnosis()), context, rendered);
        } catch (RuntimeException e) {
            return new Analysis(new BaselineScorer.CaseResult(c.id(), false, "INTERNAL_ERROR", e.getClass().getSimpleName(),
                    0, 0, -1, -1, null), "", "");
        }
    }

    private static Map<String, Object> summaryToMap(BaselineScorer.Summary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cases", s.cases());
        m.put("succeeded", s.succeeded());
        m.put("autoPass", s.autoPass());
        m.put("autoFail", s.autoFail());
        m.put("notEvaluated", s.notEvaluated());
        m.put("gapCases", s.gapCases());
        m.put("gapAutoPass", s.gapAutoPass());
        m.put("otherCases", s.otherCases());
        m.put("otherAutoPass", s.otherAutoPass());
        m.put("attempts", s.attempts());
        m.put("tokensIn", s.tokensIn());
        m.put("tokensOut", s.tokensOut());
        m.put("costUsd", Math.round(s.costUsd() * 1_000_000.0) / 1_000_000.0);
        m.put("casesWithUnknownTokens", s.casesWithUnknownTokens());
        m.put("latencyMeanMillis", s.latencyMeanMillis());
        m.put("latencyMedianMillis", s.latencyMedianMillis());
        m.put("latencyMaxMillis", s.latencyMaxMillis());
        return m;
    }

    /** Escreve o ficheiro e repete-o no log entre marcadores; cada linha com prefixo "| " e sem segredos. */
    private static void write(Path outDir, SecretFilter filter, PrintStream log, String relative, String content) throws IOException {
        String safe = filter.mask(content);
        Path file = outDir.resolve(relative);
        Files.createDirectories(file.getParent() == null ? outDir : file.getParent());
        Files.writeString(file, safe, StandardCharsets.UTF_8);
        log.println("===BASELINE-FILE " + relative + "===");
        for (String line : safe.split("\n", -1)) {
            log.println("| " + line);
        }
        log.println("===BASELINE-END===");
    }
}
