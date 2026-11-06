package com.eversonrubira.exporttracking.triage.security;

import com.eversonrubira.exporttracking.triage.context.ContextPackage;

/**
 * Contexto que ja passou pelo Secret Filter. O construtor e package-private: so o
 * {@link SecretFilter} o cria. O cliente do provider so aceita este tipo, por isso
 * enviar um contexto sem filtrar nao compila (RNF-06 garantido pelo tipo, nao por disciplina).
 */
public final class SanitizedContext {

    private final ContextPackage context;

    SanitizedContext(ContextPackage context) {
        this.context = context;
    }

    public ContextPackage context() {
        return context;
    }
}
