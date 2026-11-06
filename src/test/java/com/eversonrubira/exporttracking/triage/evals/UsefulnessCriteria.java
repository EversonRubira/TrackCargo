package com.eversonrubira.exporttracking.triage.evals;

import com.eversonrubira.exporttracking.triage.validation.Confidence;
import com.eversonrubira.exporttracking.triage.validation.Diagnosis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Avaliador dos criterios objetivos de utilidade de um caso. Devolve as violacoes
 * (lista vazia = o diagnostico cumpre o piso automatico).
 *
 * E deliberadamente simples: procura termos no texto do diagnostico. Mede um piso
 * (o diagnostico menciona o que tem de mencionar, nao inventa documentos, nao
 * excede a confianca permitida), nao a qualidade da explicacao. Isso e revisao
 * humana (manualChecks de cada caso).
 */
final class UsefulnessCriteria {

    private UsefulnessCriteria() {
    }

    static List<String> violations(EvalCase c, Diagnosis d) {
        EvalCase.Usefulness u = c.usefulness();
        List<String> violations = new ArrayList<>();
        String all = fullText(d);

        for (List<String> group : u.mustMention()) {
            if (group.stream().noneMatch(term -> contains(all, term))) {
                violations.add("must mention one of " + group);
            }
        }
        for (String term : u.causeMustNotMention()) {
            if (contains(d.probableCause(), term)) {
                violations.add("probableCause must not mention '" + term + "'");
            }
        }
        for (String term : u.mustNotMention()) {
            if (contains(all, term)) {
                violations.add("must not mention '" + term + "'");
            }
        }
        if (d.confidence().compareTo(Confidence.valueOf(u.maxConfidence())) < 0) {
            // HIGH < MEDIUM < LOW na ordem do enum: "menor" quer dizer mais confiante.
            violations.add("confidence " + d.confidence() + " is above the allowed maximum " + u.maxConfidence());
        }
        if (u.mustDeclareLimitations() && d.limitations().isEmpty()) {
            violations.add("must declare limitations");
        }
        // Nunca citar documentos que nao foram fornecidos (inclui o caso "documentacao ausente").
        for (String related : d.relatedDocuments()) {
            if (c.relatedSpecs().stream().noneMatch(spec -> pathOf(spec).equals(pathOf(related)))) {
                violations.add("relatedDocuments cites a document that was not provided: " + related);
            }
        }
        if (u.mustCiteRelatedDocument() && d.relatedDocuments().isEmpty()) {
            violations.add("must cite at least one of the provided documents");
        }
        return violations;
    }

    private static String pathOf(String reference) {
        int hash = reference.indexOf('#');
        return (hash < 0 ? reference : reference.substring(0, hash)).trim();
    }

    private static boolean contains(String text, String term) {
        return text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private static String fullText(Diagnosis d) {
        StringBuilder s = new StringBuilder(d.probableCause()).append('\n');
        d.evidence().forEach(e -> s.append(e).append('\n'));
        d.relatedDocuments().forEach(e -> s.append(e).append('\n'));
        d.possibleSolutions().forEach(sol -> {
            s.append(sol.solution()).append('\n');
            sol.tradeoffs().forEach(t -> s.append(t).append('\n'));
        });
        s.append(d.confidenceReason()).append('\n');
        d.limitations().forEach(l -> s.append(l).append('\n'));
        return s.toString();
    }
}
