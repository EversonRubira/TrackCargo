package com.eversonrubira.exporttracking.triage.observability;

import com.eversonrubira.exporttracking.triage.context.ArtifactKind;
import com.eversonrubira.exporttracking.triage.rendering.SafeText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Observability Recorder (T12, RNF-08): uma linha {@code [triage-metrics]} por falha analisada e uma
 * de resumo, no proprio log do CI (o destino de telemetria da V1). Formato chave=valor numa so linha,
 * facil de ler e de pesquisar com grep.
 *
 * Os unicos textos livres sao o id do teste (vem dos relatorios, nao confiavel) e nunca ha conteudo
 * de contexto. O id passa pelo mesmo {@link SafeText} do renderer: sem controlo, sem comandos do
 * Actions (prefixo "| ") e sem segredos. Vai no fim da linha e numa linha so.
 */
public final class ObservabilityRecorder {

    static final String TAG = "| [triage-metrics] ";

    private final SafeText safeText;

    public ObservabilityRecorder(SafeText safeText) {
        this.safeText = safeText;
    }

    public String failureLine(FailureSignal s) {
        List<String> kinds = new ArrayList<>();
        for (Map.Entry<ArtifactKind, Integer> e : s.contextByKind().entrySet()) {
            kinds.add(e.getKey().name() + ":" + e.getValue());
        }
        return TAG + "status=" + (s.success() ? "SUCCESS" : "FAILED")
                + " reason=" + s.reason()
                + " attempts=" + s.attempts()
                + " durationMs=" + s.durationMillis()
                + " contextArtifacts=" + s.contextArtifacts()
                + " context=" + String.join(",", kinds)
                + " contextChars=" + s.contextChars()
                + " tokensIn=" + s.inputTokens()
                + " tokensOut=" + s.outputTokens()
                + " test=" + oneLine(s.testId()) + "\n";
    }

    /** Estado global: NO_FAILURES, SUCCESS (todas as analisadas tiveram diagnostico) ou FAILED (alguma nao). */
    public String summaryLine(int total, int analyzed, int skipped, int succeeded, int failed, int limit, long durationMillis) {
        String status = analyzed == 0 ? "NO_FAILURES" : failed == 0 ? "SUCCESS" : "FAILED";
        return TAG + "summary status=" + status
                + " failures=" + total
                + " analyzed=" + analyzed
                + " skipped=" + skipped
                + " succeeded=" + succeeded
                + " failed=" + failed
                + " limit=" + limit
                + " durationMs=" + durationMillis + "\n";
    }

    private String oneLine(String text) {
        return safeText.clean(text).replace('\n', ' ');
    }
}
