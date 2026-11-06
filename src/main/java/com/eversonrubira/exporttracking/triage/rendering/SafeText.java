package com.eversonrubira.exporttracking.triage.rendering;

import com.eversonrubira.exporttracking.triage.security.SecretFilter;

/**
 * Limpeza unica de texto que vai para o log do CI (renderer e observabilidade usam a mesma):
 * remove caracteres de controlo e invisiveis, normaliza quebras de linha e, so depois,
 * mascara segredos. Esta ordem importa: um caractere invisivel dentro de uma chave nao a
 * pode esconder do filtro. Nao acrescenta o prefixo "| ": isso e feito por quem escreve a linha.
 */
public final class SafeText {

    private final SecretFilter filter;

    public SafeText(SecretFilter filter) {
        this.filter = filter;
    }

    /** Remove controlo/invisiveis, normaliza quebras de linha e mascara segredos. */
    public String clean(String text) {
        String value = text == null ? "" : text;
        StringBuilder sb = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\r') {
                if (i < value.length() && value.charAt(i) == '\n') {
                    i++;
                }
                sb.append('\n');
            } else if (cp == '\n' || cp == 0x85 || cp == 0x2028 || cp == 0x2029) {
                sb.append('\n');
            } else if (cp == '\t') {
                sb.append("  ");
            } else {
                int type = Character.getType(cp);
                if (type != Character.CONTROL && type != Character.FORMAT && type != Character.UNASSIGNED
                        && type != Character.SURROGATE && type != Character.PRIVATE_USE) {
                    sb.appendCodePoint(cp);
                }
            }
        }
        return filter.mask(sb.toString());
    }
}
