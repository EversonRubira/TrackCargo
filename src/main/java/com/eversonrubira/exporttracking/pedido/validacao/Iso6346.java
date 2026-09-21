package com.eversonrubira.exporttracking.pedido.validacao;

import java.util.Map;
import java.util.regex.Pattern;

// Numero de container ISO 6346: 4 letras (owner code + categoria) + 6
// digitos de serie + 1 digito verificador. Classe pura (sem Spring, sem
// anotacao) pra ficar facil de testar isolada - o Bean Validation
// (NumeroContainerIso6346Validator) e o PedidoService chamam os mesmos
// dois metodos, nenhum dos dois reimplementa a regra.
public final class Iso6346 {

    private static final Pattern FORMATO = Pattern.compile("^[A-Z]{4}\\d{7}$");

    // Valor de cada letra pro calculo do digito verificador - A=10,
    // B=12, C=13... pulando todo multiplo de 11 (11, 22, 33...), regra
    // da norma ISO 6346 (evita ambiguidade com o proprio modulo 11 do
    // calculo do digito verificador).
    private static final Map<Character, Integer> VALOR_LETRA = construirTabelaDeLetras();

    private Iso6346() {
    }

    // Maiusculas, sem espaco nem hifen. null/branco continua null (o
    // campo e opcional - cabe ao chamador decidir se isso e valido ou
    // nao pro contexto dele).
    public static String normalizar(String bruto) {
        if (bruto == null) {
            return null;
        }
        String normalizado = bruto.toUpperCase().replace(" ", "").replace("-", "");
        return normalizado.isBlank() ? null : normalizado;
    }

    // Espera a string ja normalizada (ver normalizar()) - formato (4
    // letras + 7 digitos) e digito verificador de verdade.
    public static boolean valido(String normalizado) {
        if (normalizado == null || !FORMATO.matcher(normalizado).matches()) {
            return false;
        }

        long soma = 0;
        for (int posicao = 0; posicao < 10; posicao++) {
            char caractere = normalizado.charAt(posicao);
            int valor = Character.isDigit(caractere) ? (caractere - '0') : VALOR_LETRA.get(caractere);
            soma += (long) valor * (1L << posicao);
        }

        int digitoCalculado = (int) ((soma % 11) % 10); // resto 10 vira 0
        int digitoInformado = normalizado.charAt(10) - '0';
        return digitoCalculado == digitoInformado;
    }

    private static Map<Character, Integer> construirTabelaDeLetras() {
        Map<Character, Integer> tabela = new java.util.HashMap<>();
        int valor = 10;
        for (char letra = 'A'; letra <= 'Z'; letra++) {
            if (valor % 11 == 0) {
                valor++;
            }
            tabela.put(letra, valor);
            valor++;
        }
        return tabela;
    }
}
