package com.eversonrubira.exporttracking.pedido.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Substitui a combinacao @DecimalMin+@DecimalMax: as duas anotacoes
// disparam uma de cada vez (o valor viola um limite por vez), entao o
// mapeamento generico de FieldError->codigo (ValidacaoCodigoMapper) so
// conseguiria extrair min OU max, nunca os dois - o cliente nao monta
// "deve estar entre X e Y" com um unico limite. Uma anotacao so, que
// carrega os dois limites como atributos, resolve isso sem exigir
// tratamento especial no mapeador (min/max saem juntos dos atributos
// da propria ConstraintValidatorContext, igual qualquer outra
// anotacao). Mesmo padrao de @NumeroContainerIso6346: constraint +
// validator dedicados, sem depender de mais nada do Spring.
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FaixaDecimalValidator.class)
public @interface FaixaDecimal {

    double min();

    double max();

    String message() default "fora da faixa permitida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
