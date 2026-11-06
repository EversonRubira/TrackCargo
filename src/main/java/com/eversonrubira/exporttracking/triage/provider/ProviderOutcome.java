package com.eversonrubira.exporttracking.triage.provider;

import com.eversonrubira.exporttracking.triage.validation.Diagnosis;

/**
 * Resultado de pedir um diagnostico: ou {@code diagnosis} ou {@code reason}.
 * {@code detail} e uma frase curta e segura (estado HTTP, campo invalido), nunca
 * corpo de resposta nem texto do pedido. Os tokens servem a observabilidade (T12);
 * -1 quer dizer desconhecido.
 */
public record ProviderOutcome(
        Diagnosis diagnosis,
        FailureReason reason,
        String detail,
        int attempts,
        long elapsedMillis,
        int inputTokens,
        int outputTokens) {

    public boolean succeeded() {
        return diagnosis != null;
    }
}
