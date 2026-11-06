package com.eversonrubira.exporttracking.triage.provider;

/**
 * Porque e que o provider nao devolveu um diagnostico. {@link #retryable()} e a
 * politica do ADR-003: repete-se erro transitorio, timeout e resposta estruturalmente
 * invalida; nao se repete credencial em falta ou invalida nem pedido rejeitado.
 */
public enum FailureReason {
    /** 429, 5xx (incl. 529 "overloaded") ou erro de ligacao: o provider pode recuperar. */
    PROVIDER_ERROR(true),
    TIMEOUT(true),
    /** Resposta 200 que nao cumpre o contrato (T08) ou foi cortada por limite de tokens. */
    INVALID_RESPONSE(true),
    MISSING_API_KEY(false),
    INVALID_CREDENTIALS(false),
    /** Outros 4xx: o pedido em si e o problema, repeti-lo nao ajuda. */
    REQUEST_REJECTED(false),
    INTERRUPTED(false);

    private final boolean retryable;

    FailureReason(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
