package com.eversonrubira.exporttracking.triage.validation;

import java.util.List;

/**
 * Resultado da validacao: ou um {@link Diagnosis} ou a lista de problemas.
 * As mensagens de erro falam so de campos e limites, nunca repetem o conteudo
 * recebido, para poderem ir para logs sem arrastar texto do modelo.
 */
public record ValidationResult(Diagnosis diagnosis, List<String> errors) {

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public static ValidationResult valid(Diagnosis diagnosis) {
        return new ValidationResult(diagnosis, List.of());
    }

    public static ValidationResult invalid(List<String> errors) {
        return new ValidationResult(null, errors);
    }

    public boolean isValid() {
        return diagnosis != null;
    }
}
