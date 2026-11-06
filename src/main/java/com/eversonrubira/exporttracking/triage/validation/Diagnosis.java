package com.eversonrubira.exporttracking.triage.validation;

import java.util.List;

/**
 * Diagnostico ja validado contra o contrato (RF-04). Ser valido quer dizer que a
 * FORMA esta certa; nao quer dizer que a causa provavel esteja certa. Isso e
 * qualidade semantica e mede-se nos evals (T13/T14), nao aqui.
 */
public record Diagnosis(
        String probableCause,
        List<String> evidence,
        List<String> relatedDocuments,
        List<Solution> possibleSolutions,
        Confidence confidence,
        String confidenceReason,
        List<String> limitations) {

    public Diagnosis {
        evidence = List.copyOf(evidence);
        relatedDocuments = List.copyOf(relatedDocuments);
        possibleSolutions = List.copyOf(possibleSolutions);
        limitations = List.copyOf(limitations);
    }

    /** Uma solucao possivel e os seus trade-offs (pelo menos um). */
    public record Solution(String solution, List<String> tradeoffs) {
        public Solution {
            tradeoffs = List.copyOf(tradeoffs);
        }
    }
}
