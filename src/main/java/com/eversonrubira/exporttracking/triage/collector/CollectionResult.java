package com.eversonrubira.exporttracking.triage.collector;

import java.util.List;

/**
 * Falhas distintas encontradas, por ordem estavel, mais avisos sobre
 * relatorios que nao foi possivel ler.
 */
public record CollectionResult(List<FailureInput> failures, List<String> warnings) {

    public CollectionResult {
        failures = List.copyOf(failures);
        warnings = List.copyOf(warnings);
    }
}
