package com.eversonrubira.exporttracking.pedido.validacao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Puro, sem Spring - so exercita normalizar()/valido() diretamente.
// Numeros de referencia conferidos com um script Python separado que
// implementa o mesmo algoritmo (valores de letra pulando multiplos de
// 11, soma ponderada por 2^posicao, resto 10 vira 0), nao inventados
// na mao.
class Iso6346Test {

    // CSQU3054383: exemplo canonico de ISO 6346 valido, citado em
    // referencias da norma - C=13,S=30,Q=28,U=32 + 3054383, digito
    // verificador 3 confirmado pelo calculo de referencia.
    private static final String VALIDO_1 = "CSQU3054383";
    // MSCU1234566: segundo numero valido, prefixo de dono diferente,
    // pra nao depender de um unico exemplo.
    private static final String VALIDO_2 = "MSCU1234566";

    @Test
    void aceitaNumeroValidoConhecido() {
        assertThat(Iso6346.valido(VALIDO_1)).isTrue();
        assertThat(Iso6346.valido(VALIDO_2)).isTrue();
    }

    @Test
    void rejeitaDigitoVerificadorErrado() {
        String comDigitoErrado = VALIDO_1.substring(0, 10) + "9"; // 3 -> 9, resto igual
        assertThat(Iso6346.valido(comDigitoErrado)).isFalse();
    }

    @Test
    void rejeitaComLetrasAMenos() {
        assertThat(Iso6346.valido("CSU3054383")).isFalse(); // 3 letras + 7 digitos, so 10 chars
    }

    @Test
    void rejeitaComDigitosAMenos() {
        assertThat(Iso6346.valido("CSQU305438")).isFalse(); // 4 letras + 6 digitos, so 10 chars
    }

    @Test
    void normalizarAceitaMinusculasEspacosEHifen() {
        String bruto = "csqu 305438-3";

        String normalizado = Iso6346.normalizar(bruto);

        assertThat(normalizado).isEqualTo(VALIDO_1);
        assertThat(Iso6346.valido(normalizado)).isTrue();
    }

    @Test
    void normalizarComNuloOuVazioDevolveNulo() {
        assertThat(Iso6346.normalizar(null)).isNull();
        assertThat(Iso6346.normalizar("")).isNull();
        assertThat(Iso6346.normalizar("   ")).isNull();
    }

    @Test
    void validoComNuloDevolveFalso() {
        assertThat(Iso6346.valido(null)).isFalse();
    }

    @Test
    void rejeitaTextoQueNaoSegueOFormato() {
        assertThat(Iso6346.valido("PLASTICO")).isFalse();
        assertThat(Iso6346.normalizar("Plastico")).isEqualTo("PLASTICO");
        assertThat(Iso6346.valido(Iso6346.normalizar("Plastico"))).isFalse();
    }
}
