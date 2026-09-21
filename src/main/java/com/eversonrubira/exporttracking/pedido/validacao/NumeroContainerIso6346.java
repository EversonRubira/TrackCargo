package com.eversonrubira.exporttracking.pedido.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NumeroContainerIso6346Validator.class)
public @interface NumeroContainerIso6346 {

    String message() default "formato invalido - padrao ISO 6346 esperado "
            + "(4 letras + 6 digitos + digito verificador), ex: CSQU3054383";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
