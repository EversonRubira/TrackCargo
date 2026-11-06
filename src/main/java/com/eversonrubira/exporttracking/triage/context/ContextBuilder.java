package com.eversonrubira.exporttracking.triage.context;

import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.collector.Gap;
import com.eversonrubira.exporttracking.triage.config.TriageConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Context Builder (T04): junta a evidencia da falha e o que o retriever recuperou
 * no pacote enviado a seguir ao filtro de segredos. So passa o necessario
 * (RNF-05) e declara tudo o que falta como limitacao, para o modelo nao tratar
 * ausencia de contexto como prova.
 */
public final class ContextBuilder {

    private final TriageConfig config;
    private final DeterministicRetriever retriever;

    public ContextBuilder(TriageConfig config, DeterministicRetriever retriever) {
        this.config = config;
        this.retriever = retriever;
    }

    public ContextPackage build(FailureInput failure) {
        List<String> limitations = new ArrayList<>();
        for (Gap gap : failure.gaps()) {
            limitations.add(gap.description());
        }

        FailureInput bounded = boundStack(failure, limitations);

        RetrievalResult retrieved = retriever.retrieve(failure);
        limitations.addAll(retrieved.limitations());

        List<Artifact> artifacts = new ArrayList<>(retrieved.artifacts());
        artifacts.sort(Comparator.comparing(Artifact::kind)); // estavel: mantem a ordem dentro de cada tipo
        artifacts = fitTotalBudget(bounded, artifacts, limitations);

        if (artifacts.isEmpty()) {
            limitations.add("No source or documentation context could be retrieved; any analysis rests on the failure evidence alone.");
        }
        return new ContextPackage(bounded, artifacts, limitations);
    }

    private FailureInput boundStack(FailureInput failure, List<String> limitations) {
        List<String> lines = failure.stackTrace().lines().toList();
        if (lines.size() <= config.maxStackLines()) {
            return failure;
        }
        limitations.add("The stack trace was cut to its first " + config.maxStackLines() + " of " + lines.size() + " lines.");
        String cut = String.join("\n", lines.subList(0, config.maxStackLines()));
        return new FailureInput(failure.testClass(), failure.testMethod(), failure.errorType(),
                failure.errorMessage(), cut, failure.logExcerpt(), failure.gaps());
    }

    /** Mantem artefactos por prioridade ate ao limite total; o resto sai e fica declarado. */
    private List<Artifact> fitTotalBudget(FailureInput failure, List<Artifact> sorted, List<String> limitations) {
        int used = failure.stackTrace().length() + failure.logExcerpt().length() + failure.errorMessage().length();
        List<Artifact> kept = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (Artifact artifact : sorted) {
            if (used + artifact.content().length() <= config.maxTotalChars()) {
                kept.add(artifact);
                used += artifact.content().length();
            } else {
                dropped.add(artifact.path());
            }
        }
        if (!dropped.isEmpty()) {
            limitations.add(dropped.size() + " artifact(s) were omitted because the total context limit of "
                    + config.maxTotalChars() + " characters was reached: " + String.join(", ", dropped));
        }
        return kept;
    }
}
