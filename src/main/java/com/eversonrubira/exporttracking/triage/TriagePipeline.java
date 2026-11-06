package com.eversonrubira.exporttracking.triage;

import com.eversonrubira.exporttracking.triage.collector.CollectionResult;
import com.eversonrubira.exporttracking.triage.collector.FailureCollector;
import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import com.eversonrubira.exporttracking.triage.context.ContextBuilder;
import com.eversonrubira.exporttracking.triage.context.ContextPackage;
import com.eversonrubira.exporttracking.triage.context.DeterministicRetriever;
import com.eversonrubira.exporttracking.triage.context.DocumentMap;
import com.eversonrubira.exporttracking.triage.observability.FailureSignal;
import com.eversonrubira.exporttracking.triage.observability.ObservabilityRecorder;
import com.eversonrubira.exporttracking.triage.provider.ClaudeClient;
import com.eversonrubira.exporttracking.triage.provider.ProviderOutcome;
import com.eversonrubira.exporttracking.triage.rendering.SafeText;
import com.eversonrubira.exporttracking.triage.rendering.TriageRenderer;
import com.eversonrubira.exporttracking.triage.security.SanitizedContext;
import com.eversonrubira.exporttracking.triage.security.SecretFilter;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pipeline do AI Test Triage (T10, T11, T12): recolhe as falhas, constroi o contexto, filtra
 * segredos, pede o diagnostico, valida, mostra e regista sinais operacionais.
 *
 * Garantias:
 *  - RF-08 / ADR-003: nunca lanca e nunca altera o resultado dos testes. Cada falha e analisada
 *    isoladamente; um erro numa nao impede as outras nem o resumo.
 *  - ADR-004 / T11: no maximo {@code maxFailures} falhas distintas sao analisadas, pela ordem dos
 *    relatorios; as restantes sao listadas como saltadas. Sem agrupamento nem deduplicacao
 *    alem de repeticoes exatas (feita no coletor).
 *  - RNF-06: o filtro de segredos e construido com o ambiente recebido, e todo o texto que sai
 *    (diagnostico, log do cliente, metricas) passa por ele.
 */
public final class TriagePipeline {

    static final String REASON_CONTEXT_ERROR = "CONTEXT_ERROR";
    static final String REASON_INTERNAL_ERROR = "INTERNAL_ERROR";

    private final TriageConfig config;
    private final Map<String, String> environment;
    private final PrintStream out;

    private final Function<FailureInput, ContextPackage> contextOverride;

    public TriagePipeline(TriageConfig config, Map<String, String> environment, PrintStream out) {
        this(config, environment, out, null);
    }

    /** Ponto de injecao so para testes: substitui a construcao de contexto para provocar erros. */
    TriagePipeline(TriageConfig config, Map<String, String> environment, PrintStream out,
                   Function<FailureInput, ContextPackage> contextOverride) {
        this.config = config;
        this.environment = environment;
        this.out = out;
        this.contextOverride = contextOverride;
    }

    /** Devolve sempre 0: o resultado do pipeline de CI e decidido pelos testes. */
    public int run() {
        if (!config.enabled()) {
            out.println("AI TEST TRIAGE: disabled (triage.enabled=false)");
            return 0;
        }
        long started = System.nanoTime();
        SecretFilter filter = new SecretFilter(environment);
        SafeText safeText = new SafeText(filter);
        TriageRenderer renderer = new TriageRenderer(filter);
        ObservabilityRecorder recorder = new ObservabilityRecorder(safeText);
        try {
            analyzeAll(filter, safeText, renderer, recorder, started);
        } catch (RuntimeException e) {
            // Ultima rede: nem um erro inesperado pode esconder a falha original dos testes.
            out.print(renderer.failure("(all)", "Unexpected internal error in the triage.",
                    e.getClass().getSimpleName(), 0));
        }
        out.flush();
        return 0;
    }

    private void analyzeAll(SecretFilter filter, SafeText safeText, TriageRenderer renderer,
                            ObservabilityRecorder recorder, long started) {
        CollectionResult collected = new FailureCollector(config.maxLogLines()).collect(config.reportsDir());
        List<FailureInput> failures = collected.failures();
        int total = failures.size();
        if (total == 0) {
            out.print(renderer.noFailures(config.reportsDir().toString(), collected.warnings()));
            out.print(recorder.summaryLine(0, 0, 0, 0, 0, config.maxFailures(), millisSince(started)));
            return;
        }

        int analyzedCount = Math.min(total, config.maxFailures());
        List<FailureInput> toAnalyze = failures.subList(0, analyzedCount);
        List<String> skipped = failures.subList(analyzedCount, total).stream().map(FailureInput::testId).toList();
        out.print(renderer.overview(total, analyzedCount, config.maxFailures(), skipped, collected.warnings()));

        Function<FailureInput, ContextPackage> contextBuilder = contextOverride != null ? contextOverride
                : new ContextBuilder(config, new DeterministicRetriever(config, DocumentMap.load(config.docMapFile())))::build;
        ClaudeClient client = new ClaudeClient(config, environment.get(config.apiKeyEnv()), logSink(out, safeText));

        int succeeded = 0;
        for (FailureInput failure : toAnalyze) {
            FailureSignal signal = analyzeOne(failure, filter, contextBuilder, client, renderer);
            if (signal.success()) {
                succeeded++;
            }
            out.print(recorder.failureLine(signal));
        }
        out.print(recorder.summaryLine(total, analyzedCount, skipped.size(), succeeded, analyzedCount - succeeded,
                config.maxFailures(), millisSince(started)));
    }

    /**
     * Destino das linhas de log do cliente. O cliente ja so escreve estado e contagens; mesmo assim
     * cada linha passa por limpeza/mascara e leva o prefixo "| " (defesa em profundidade, RNF-06).
     */
    static Consumer<String> logSink(PrintStream out, SafeText safeText) {
        return line -> out.println("| " + safeText.clean(line).replace('\n', ' '));
    }

    private FailureSignal analyzeOne(FailureInput failure, SecretFilter filter, Function<FailureInput, ContextPackage> contextBuilder,
                                     ClaudeClient client, TriageRenderer renderer) {
        long started = System.nanoTime();
        String testId = failure.testId();
        ContextPackage context;
        try {
            context = contextBuilder.apply(failure);
        } catch (RuntimeException e) {
            out.print(renderer.failure(testId, "Could not build the context for this failure.", e.getClass().getSimpleName(), 0));
            return new FailureSignal(testId, false, REASON_CONTEXT_ERROR, 0, millisSince(started), Map.of(), 0, -1, -1);
        }
        ContextPackage.Summary summary = null;
        try {
            summary = context.summary();
            SanitizedContext sanitized = filter.sanitize(context);
            ProviderOutcome outcome = client.diagnose(sanitized);
            if (outcome.succeeded()) {
                out.print(renderer.success(testId, outcome.diagnosis()));
            } else {
                out.print(renderer.failure(testId, outcome));
            }
            return new FailureSignal(testId, outcome.succeeded(),
                    outcome.succeeded() ? "none" : outcome.reason().name(),
                    outcome.attempts(), millisSince(started), summary.artifactsByKind(), summary.totalChars(),
                    outcome.inputTokens(), outcome.outputTokens());
        } catch (RuntimeException e) {
            out.print(renderer.failure(testId, "Unexpected internal error in the triage.", e.getClass().getSimpleName(), 0));
            return new FailureSignal(testId, false, REASON_INTERNAL_ERROR, 0, millisSince(started),
                    summary == null ? Map.of() : summary.artifactsByKind(), summary == null ? 0 : summary.totalChars(), -1, -1);
        }
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
