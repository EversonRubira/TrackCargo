package com.eversonrubira.exporttracking.triage.context;

/**
 * Um pedaco de contexto recuperado. {@code path} e relativo a raiz do projeto,
 * {@code reason} diz porque foi incluido e {@code truncated} indica corte por limite.
 * O conteudo e texto nao confiavel (RNF-01).
 */
public record Artifact(ArtifactKind kind, String path, String reason, String content, boolean truncated) {

    public Artifact withContent(String newPath, String newReason, String newContent) {
        return new Artifact(kind, newPath, newReason, newContent, truncated);
    }
}
