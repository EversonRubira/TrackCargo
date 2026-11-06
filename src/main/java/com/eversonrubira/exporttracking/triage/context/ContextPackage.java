package com.eversonrubira.exporttracking.triage.context;

import com.eversonrubira.exporttracking.triage.collector.FailureInput;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pacote de contexto de uma falha: a evidencia, os artefactos recuperados e as
 * limitacoes conhecidas. Tudo aqui e texto nao confiavel (RNF-01).
 */
public record ContextPackage(FailureInput failure, List<Artifact> artifacts, List<String> limitations) {

    public ContextPackage {
        artifacts = List.copyOf(artifacts);
        limitations = List.copyOf(limitations);
    }

    /** Quantidades aproximadas por categoria, para observabilidade (RNF-08). Sem conteudo. */
    public Summary summary() {
        Map<ArtifactKind, Integer> byKind = new EnumMap<>(ArtifactKind.class);
        for (ArtifactKind kind : ArtifactKind.values()) {
            byKind.put(kind, 0);
        }
        int artifactChars = 0;
        for (Artifact a : artifacts) {
            byKind.merge(a.kind(), 1, Integer::sum);
            artifactChars += a.content().length();
        }
        return new Summary(byKind, artifactChars, toInspectableText().length());
    }

    public record Summary(Map<ArtifactKind, Integer> artifactsByKind, int artifactChars, int totalChars) {
    }

    /**
     * Texto estavel para inspecao humana e testes. NAO e o prompt: a fronteira entre
     * instrucoes de confianca e dados nao confiaveis define-se no T06.
     */
    public String toInspectableText() {
        StringBuilder s = new StringBuilder();
        s.append("=== FAILURE ===\n");
        s.append("test: ").append(failure.testId()).append('\n');
        s.append("errorType: ").append(failure.errorType()).append('\n');
        s.append("errorMessage: ").append(failure.errorMessage()).append('\n');
        s.append("--- stackTrace ---\n").append(failure.stackTrace()).append('\n');
        s.append("--- logExcerpt ---\n").append(failure.logExcerpt()).append('\n');
        s.append("=== CONTEXT ARTIFACTS (").append(artifacts.size()).append(") ===\n");
        for (Artifact a : artifacts) {
            s.append("--- [").append(a.kind()).append("] ").append(a.path())
                    .append(" | ").append(a.reason())
                    .append(a.truncated() ? " | TRUNCATED" : "").append(" ---\n");
            s.append(a.content()).append('\n');
        }
        s.append("=== LIMITATIONS (").append(limitations.size()).append(") ===\n");
        for (String l : limitations) {
            s.append("- ").append(l).append('\n');
        }
        return s.toString();
    }
}
