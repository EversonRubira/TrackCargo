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

    /**
     * Le qualquer JSON para Map/List/String/Number/Boolean/null. Falha com IllegalArgumentException.
     * Um objeto ou lista seguido de texto ({"a":1} obrigado!) tambem falha: o Jackson ignora
     * conteudo final conforme a configuracao, e o contrato da resposta e "so o objeto".
     */
    public static Object read(String json) {
        try {
            requireNothingAfterTheFirstValue(json);
            return MAPPER.readValue(json, Object.class);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }

    /**
     * Para objetos e listas, confirma que so ha espacos depois de o valor fechar. Percorre o texto
     * a respeitar strings e escapes, por isso chavetas dentro de strings nao contam.
     * Escalares nao precisam disto (o validador exige um objeto).
     */
    static void requireNothingAfterTheFirstValue(String json) {
        int start = 0;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length() || (json.charAt(start) != '{' && json.charAt(start) != '[')) {
            return;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    if (!json.substring(i + 1).isBlank()) {
                        throw new IllegalArgumentException("Unexpected content after the JSON value");
                    }
                    return;
                }
            }
        }
        // valor por fechar: deixa o parser do Jackson dar o erro
    }
}
