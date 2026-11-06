package com.eversonrubira.exporttracking.triage.validation;

import com.eversonrubira.exporttracking.triage.json.JsonSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Valida a resposta do Claude contra o contrato do spec (RF-04, RF-05) antes de a
 * apresentar como diagnostico. Resposta invalida nunca chega ao renderer: o cliente
 * trata-a como "resposta estruturalmente invalida" e aplica a politica de retry (ADR-003).
 *
 * Valida a FORMA, nao a verdade. Uma resposta com a causa errada mas bem formada passa;
 * medir isso e o papel dos evals.
 *
 * Tolerancias deliberadas: um cerco de codigo (```json ... ```) a volta do objeto e aceite,
 * e campos desconhecidos sao ignorados. Texto antes ou depois do objeto nao e aceite:
 * o prompt pede so o objeto, e um desvio disto vale uma segunda tentativa.
 */
public final class DiagnosisValidator {

    /** Limites que protegem o log do CI de respostas desmedidas. */
    public static final int MAX_ITEMS = 10;
    public static final int MAX_TEXT_CHARS = 2000;

    private DiagnosisValidator() {
    }

    public static ValidationResult validate(String raw) {
        if (raw == null || raw.isBlank()) {
            return ValidationResult.invalid(List.of("response is empty"));
        }
        Object parsed;
        try {
            parsed = JsonSupport.read(stripFence(raw.strip()));
        } catch (IllegalArgumentException e) {
            return ValidationResult.invalid(List.of("response is not valid JSON"));
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            return ValidationResult.invalid(List.of("response is not a JSON object"));
        }

        List<String> errors = new ArrayList<>();
        String cause = text(root, "probableCause", errors);
        List<String> evidence = textList(root, "evidence", true, errors);
        List<String> documents = textList(root, "relatedDocuments", false, errors);
        List<Diagnosis.Solution> solutions = solutions(root, errors);
        Confidence confidence = confidence(root, errors);
        String reason = text(root, "confidenceReason", errors);
        List<String> limitations = textList(root, "limitations", false, errors);

        if (!errors.isEmpty()) {
            return ValidationResult.invalid(errors);
        }
        return ValidationResult.valid(new Diagnosis(cause, evidence, documents, solutions, confidence, reason, limitations));
    }

    /** Remove um unico cerco ```...``` a volta de tudo, se existir. */
    static String stripFence(String text) {
        if (!text.startsWith("```") || !text.endsWith("```") || text.length() < 6) {
            return text;
        }
        String inner = text.substring(3, text.length() - 3);
        int newline = inner.indexOf('\n');
        // a primeira linha e o nome da linguagem (json) ou vazia
        if (newline >= 0 && inner.substring(0, newline).strip().matches("[A-Za-z]*")) {
            inner = inner.substring(newline + 1);
        }
        return inner.strip();
    }

    private static String text(Map<?, ?> root, String field, List<String> errors) {
        Object value = root.get(field);
        if (!(value instanceof String s) || s.isBlank()) {
            errors.add(field + " must be a non-empty string");
            return null;
        }
        if (s.length() > MAX_TEXT_CHARS) {
            errors.add(field + " is longer than " + MAX_TEXT_CHARS + " characters");
            return null;
        }
        return s.strip();
    }

    private static List<String> textList(Map<?, ?> root, String field, boolean required, List<String> errors) {
        Object value = root.get(field);
        if (!(value instanceof List<?> items)) {
            errors.add(field + " must be an array");
            return List.of();
        }
        if (required && items.isEmpty()) {
            errors.add(field + " must have at least one item");
            return List.of();
        }
        if (items.size() > MAX_ITEMS) {
            errors.add(field + " has more than " + MAX_ITEMS + " items");
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!(item instanceof String s) || s.isBlank()) {
                errors.add(field + "[" + i + "] must be a non-empty string");
            } else if (s.length() > MAX_TEXT_CHARS) {
                errors.add(field + "[" + i + "] is longer than " + MAX_TEXT_CHARS + " characters");
            } else {
                out.add(s.strip());
            }
        }
        return out;
    }

    private static List<Diagnosis.Solution> solutions(Map<?, ?> root, List<String> errors) {
        Object value = root.get("possibleSolutions");
        if (!(value instanceof List<?> items)) {
            errors.add("possibleSolutions must be an array");
            return List.of();
        }
        if (items.isEmpty()) {
            errors.add("possibleSolutions must have at least one item");
            return List.of();
        }
        if (items.size() > MAX_ITEMS) {
            errors.add("possibleSolutions has more than " + MAX_ITEMS + " items");
            return List.of();
        }
        List<Diagnosis.Solution> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String where = "possibleSolutions[" + i + "]";
            if (!(items.get(i) instanceof Map<?, ?> item)) {
                errors.add(where + " must be an object");
                continue;
            }
            int before = errors.size();
            String solution = text(item, "solution", errors);
            List<String> tradeoffs = textList(item, "tradeoffs", true, errors);
            if (errors.size() > before) {
                // reescreve as mensagens novas com o indice, para se saber qual solucao falhou
                for (int e = before; e < errors.size(); e++) {
                    errors.set(e, where + "." + errors.get(e));
                }
            } else {
                out.add(new Diagnosis.Solution(solution, tradeoffs));
            }
        }
        return out;
    }

    private static Confidence confidence(Map<?, ?> root, List<String> errors) {
        Object value = root.get("confidence");
        if (value instanceof String s) {
            for (Confidence c : Confidence.values()) {
                if (c.name().equals(s)) {
                    return c;
                }
            }
        }
        errors.add("confidence must be one of HIGH, MEDIUM, LOW");
        return null;
    }
}
