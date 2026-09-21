package com.eversonrubira.exporttracking.pedido.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// Campo opcional (so existe depois da reserva) - null/vazio passa.
// Normaliza antes de checar o formato, pra aceitar o mesmo jeito que
// o service vai gravar (mesma classe pura Iso6346 dos dois lados).
public class NumeroContainerIso6346Validator implements ConstraintValidator<NumeroContainerIso6346, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        String normalizado = Iso6346.normalizar(valor);
        return normalizado == null || Iso6346.valido(normalizado);
    }
}
