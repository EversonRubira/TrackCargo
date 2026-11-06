package com.eversonrubira.exporttracking.triage.collector;

import java.util.List;

/**
 * Entrada normalizada de um teste falhado (RF-02). Campos de texto nunca sao
 * null: o que falta fica vazio e e declarado em {@code gaps}.
 */
public record FailureInput(
        String testClass,
        String testMethod,
        String errorType,
        String errorMessage,
        String stackTrace,
        String logExcerpt,
        List<Gap> gaps) {

    public FailureInput {
        gaps = List.copyOf(gaps);
    }

    /** Identidade do teste: classe + nome, como vem do Surefire. */
    public String testId() {
        return testClass + "#" + testMethod;
    }
}
