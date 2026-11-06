package com.eversonrubira.exporttracking.triage.collector;

/** Informacao em falta num teste falhado. Alimenta as limitacoes do contexto. */
public enum Gap {
    MESSAGE_MISSING("The test report has no error message."),
    STACK_TRACE_MISSING("The test report has no stack trace, so classes cited by it cannot be identified."),
    LOG_MISSING("No execution log output was available for this test.");

    private final String description;

    Gap(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
