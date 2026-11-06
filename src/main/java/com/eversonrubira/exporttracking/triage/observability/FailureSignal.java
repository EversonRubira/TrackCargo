package com.eversonrubira.exporttracking.triage.observability;

import com.eversonrubira.exporttracking.triage.context.ArtifactKind;

import java.util.EnumMap;
import java.util.Map;

/**
 * Sinais operacionais de uma falha analisada (T12, RNF-08). So numeros, nomes de enum e o
 * identificador do teste: nada de conteudo de contexto, de resposta do modelo ou de segredos.
 *
 * @param reason "none" em sucesso; senao o motivo operacional (nome de FailureReason ou do pipeline)
 */
public record FailureSignal(
        String testId,
        boolean success,
        String reason,
        int attempts,
        long durationMillis,
        Map<ArtifactKind, Integer> contextByKind,
        int contextChars,
        int inputTokens,
        int outputTokens) {

    public FailureSignal {
        EnumMap<ArtifactKind, Integer> copy = new EnumMap<>(ArtifactKind.class);
        for (ArtifactKind kind : ArtifactKind.values()) {
            copy.put(kind, contextByKind.getOrDefault(kind, 0));
        }
        contextByKind = copy;
    }

    public int contextArtifacts() {
        return contextByKind.values().stream().mapToInt(Integer::intValue).sum();
    }
}
