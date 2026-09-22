package com.eversonrubira.exporttracking.pedido.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

// Nulo e valido de proposito - obrigatoriedade e responsabilidade do
// @NotNull no mesmo campo, nao duplicamos a checagem aqui. Faixa
// inclusiva nos dois extremos (min e max entram na faixa valida).
public class FaixaDecimalValidator implements ConstraintValidator<FaixaDecimal, BigDecimal> {

    private BigDecimal min;
    private BigDecimal max;

    @Override
    public void initialize(FaixaDecimal anotacao) {
        this.min = BigDecimal.valueOf(anotacao.min());
        this.max = BigDecimal.valueOf(anotacao.max());
    }

    @Override
    public boolean isValid(BigDecimal valor, ConstraintValidatorContext context) {
        return valor == null || (valor.compareTo(min) >= 0 && valor.compareTo(max) <= 0);
    }
}
