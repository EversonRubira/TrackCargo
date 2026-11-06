package com.eversonrubira.exporttracking.triage.json;

import tools.jackson.databind.json.JsonMapper;

/**
 * Unico ponto do triage que toca no Jackson (o mesmo que o projeto ja usa, versao 3).
 * Confinar aqui limita o que muda se a API do Jackson mudar. So duas operacoes,
 * sobre tipos simples (Map, List, String, numeros, booleanos, null).
 */
public final class JsonSupport {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private JsonSupport() {
    }

    /** Serializa Map/List/String/numeros. Falha com IllegalStateException. */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize JSON", e);
        }
    }

    /** Le qualquer JSON para Map/List/String/Number/Boolean/null. Falha com IllegalArgumentException. */
    public static Object read(String json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }
}
